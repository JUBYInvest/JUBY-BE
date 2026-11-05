package juby.invest.domain.community.service;

import juby.invest.domain.community.dto.PostReqDto;
import juby.invest.domain.community.dto.PostResDto;
import juby.invest.domain.community.entity.Post;
import juby.invest.domain.community.exception.CommunityException;
import juby.invest.domain.community.exception.code.CommunityErrorCode;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.exception.StockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/***
 * 커서 페이징, soft delete 필터, 좋아요 수 벌크 UPDATE는 쿼리가 실제로 어떻게 동작하는지가 핵심이라
 * Repository를 목으로 바꾸지 않고 H2 위에서 서비스를 그대로 돌린다.
 */
@DataJpaTest
@Import(PostService.class)
@DisplayName("PostService")
class PostServiceTest {

    @Autowired private TestEntityManager em;
    @Autowired private PostService postService;

    private Member writer;
    private Member other;
    private Member noNickname;
    private Stock samsung;
    private Stock hynix;

    @BeforeEach
    void setUp() {
        writer = em.persist(Member.builder().name("작성자").nickname("용감한개미").build());
        other = em.persist(Member.builder().name("다른회원").nickname("신중한개미").build());
        noNickname = em.persist(Member.builder().name("닉네임없음").build());
        samsung = em.persist(Stock.builder().stockCode("005930").stockName("삼성전자").build());
        hynix = em.persist(Stock.builder().stockCode("000660").stockName("SK하이닉스").build());
        em.flush();
    }

    private Long write(Stock stock, String title) {
        return postService.createPost(writer.getId(), new PostReqDto.CreateReq(stock.getStockCode(), title, "내용")).postId();
    }

    private CommunityErrorCode errorCodeOf(Throwable e) {
        return (CommunityErrorCode) ((CommunityException) e).getErrorCode();
    }

    @Nested
    @DisplayName("목록 조회")
    class GetPosts {

        @Test
        @DisplayName("stockCode가 없으면 전체 종목 게시물을 최신순으로 반환한다")
        void allStocksLatestFirst() {
            write(samsung, "첫 글");
            write(hynix, "둘째 글");
            write(samsung, "셋째 글");

            PostResDto.PostList res = postService.getPosts(null, null, null, 20);

            assertThat(res.posts()).extracting(PostResDto.PostSummary::title)
                    .containsExactly("셋째 글", "둘째 글", "첫 글");
            assertThat(res.posts().get(1).stockName()).isEqualTo("SK하이닉스");
            assertThat(res.hasNext()).isFalse();
            assertThat(res.nextCursor()).isNull();
        }

        @Test
        @DisplayName("stockCode가 있으면 해당 종목 게시물만 반환한다")
        void filtersByStock() {
            write(samsung, "삼성 글");
            write(hynix, "하이닉스 글");

            PostResDto.PostList res = postService.getPosts(null, "000660", null, 20);

            assertThat(res.posts()).extracting(PostResDto.PostSummary::title).containsExactly("하이닉스 글");
        }

        @Test
        @DisplayName("nextCursor로 다음 페이지를 이어서 조회하면 중복/누락이 없다")
        void cursorPaging() {
            for (int i = 1; i <= 5; i++) {
                write(samsung, "글" + i);
            }

            PostResDto.PostList first = postService.getPosts(null, null, null, 2);
            PostResDto.PostList second = postService.getPosts(null, null, first.nextCursor(), 2);
            PostResDto.PostList last = postService.getPosts(null, null, second.nextCursor(), 2);

            assertThat(first.posts()).extracting(PostResDto.PostSummary::title).containsExactly("글5", "글4");
            assertThat(first.hasNext()).isTrue();
            assertThat(second.posts()).extracting(PostResDto.PostSummary::title).containsExactly("글3", "글2");
            assertThat(last.posts()).extracting(PostResDto.PostSummary::title).containsExactly("글1");
            assertThat(last.hasNext()).isFalse();
            assertThat(last.nextCursor()).isNull();
        }

        @Test
        @DisplayName("삭제된 게시물은 목록에 나오지 않는다")
        void excludesDeleted() {
            Long deleted = write(samsung, "지울 글");
            write(samsung, "남길 글");
            postService.deletePost(writer.getId(), deleted);

            assertThat(postService.getPosts(null, null, null, 20).posts())
                    .extracting(PostResDto.PostSummary::title).containsExactly("남길 글");
        }

        @Test
        @DisplayName("로그인 사용자에게는 내 글 여부와 내 좋아요 여부를 표시한다")
        void viewerFlags() {
            Long liked = write(samsung, "좋아요한 글");
            write(samsung, "안 누른 글");
            postService.toggleLike(other.getId(), liked);

            List<PostResDto.PostSummary> asOther = postService.getPosts(other.getId(), null, null, 20).posts();
            List<PostResDto.PostSummary> asGuest = postService.getPosts(null, null, null, 20).posts();

            assertThat(asOther).extracting(PostResDto.PostSummary::likedByMe).containsExactly(false, true);
            assertThat(asOther).extracting(PostResDto.PostSummary::isMine).containsOnly(false);
            assertThat(asGuest).extracting(PostResDto.PostSummary::likedByMe).containsOnly(false);
            assertThat(postService.getPosts(writer.getId(), null, null, 20).posts())
                    .extracting(PostResDto.PostSummary::isMine).containsOnly(true);
        }

        @Test
        @DisplayName("본문은 100자까지만 미리보기로 내려준다")
        void preview() {
            postService.createPost(writer.getId(), new PostReqDto.CreateReq("005930", "긴 글", "가".repeat(150)));

            String preview = postService.getPosts(null, null, null, 20).posts().getFirst().preview();

            assertThat(preview).isEqualTo("가".repeat(100) + "...");
        }
    }

    private Long writeWithoutStock(String title) {
        return postService.createPost(writer.getId(), new PostReqDto.CreateReq(null, title, "내용")).postId();
    }

    private void likeBy(Long postId, Member... members) {
        for (Member member : members) {
            postService.toggleLike(member.getId(), postId);
        }
    }

    // 작성 시각은 @PrePersist가 현재 시각으로 찍으므로, 지난주 글은 저장 후 DB 값을 직접 바꿔 만든다.
    private void moveCreatedAt(Long postId, LocalDateTime createdAt) {
        em.getEntityManager().createNativeQuery("update post set created_at = ?1 where id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, postId)
                .executeUpdate();
        em.clear();
    }

    @Nested
    @DisplayName("종목 없는 글")
    class WithoutStock {

        @Test
        @DisplayName("종목을 고르지 않아도 작성할 수 있고, 종목 정보는 비어 있다")
        void creates() {
            PostResDto.PostDetail res = postService.createPost(writer.getId(), new PostReqDto.CreateReq(null, "제목", "내용"));

            assertThat(res.stockCode()).isNull();
            assertThat(res.stockName()).isNull();
            assertThat(postService.getPost(null, res.postId()).title()).isEqualTo("제목");
        }

        @Test
        @DisplayName("전체 목록에는 나오고, 종목별 목록에는 나오지 않는다")
        void onlyInAllFeed() {
            write(samsung, "삼성 글");
            writeWithoutStock("종목 없는 글");

            assertThat(postService.getPosts(null, null, null, 20).posts())
                    .extracting(PostResDto.PostSummary::title).containsExactly("종목 없는 글", "삼성 글");
            assertThat(postService.getPosts(null, "005930", null, 20).posts())
                    .extracting(PostResDto.PostSummary::title).containsExactly("삼성 글");
        }
    }

    @Nested
    @DisplayName("이번 주 인기글")
    class Popular {

        @Test
        @DisplayName("좋아요 많은 순으로, 같으면 최신 글이 먼저 나온다")
        void orderedByLikes() {
            Long one = write(samsung, "좋아요 1");
            Long two = write(hynix, "좋아요 2");
            write(samsung, "좋아요 0");
            Long alsoOne = writeWithoutStock("좋아요 1 (최신)");
            likeBy(one, other);
            likeBy(two, other, noNickname);
            likeBy(alsoOne, noNickname);

            assertThat(postService.getPopularPosts(null, null, null, null, 20).posts())
                    .extracting(PostResDto.PostSummary::title)
                    .containsExactly("좋아요 2", "좋아요 1 (최신)", "좋아요 1", "좋아요 0");
        }

        @Test
        @DisplayName("이번 주 월요일 이전에 작성된 글은 좋아요가 많아도 빠진다")
        void onlyThisWeek() {
            Long old = write(samsung, "지난주 글");
            likeBy(old, other, noNickname);
            write(samsung, "이번 주 글");
            moveCreatedAt(old, PostService.startOfWeek(LocalDateTime.now()).minusSeconds(1));

            assertThat(postService.getPopularPosts(null, null, null, null, 20).posts())
                    .extracting(PostResDto.PostSummary::title).containsExactly("이번 주 글");
        }

        @Test
        @DisplayName("stockCode가 있으면 해당 종목 인기글만 나온다")
        void filtersByStock() {
            Long hynixPost = write(hynix, "하이닉스 글");
            likeBy(hynixPost, other);
            write(samsung, "삼성 글");
            writeWithoutStock("종목 없는 글");

            assertThat(postService.getPopularPosts(null, "005930", null, null, 20).posts())
                    .extracting(PostResDto.PostSummary::title).containsExactly("삼성 글");
        }

        @Test
        @DisplayName("좋아요 수가 같은 글이 페이지 경계에 걸려도 (좋아요 수, id) 커서로 중복/누락 없이 이어진다")
        void cursorPaging() {
            Long top = write(samsung, "좋아요 1");
            likeBy(top, other);
            for (int i = 1; i <= 4; i++) {
                write(samsung, "좋아요 0-" + i);
            }

            PostResDto.PopularPostList first = postService.getPopularPosts(null, null, null, null, 2);
            PostResDto.PopularPostList second = postService.getPopularPosts(null, null, first.nextCursorLikeCount(), first.nextCursorId(), 2);
            PostResDto.PopularPostList last = postService.getPopularPosts(null, null, second.nextCursorLikeCount(), second.nextCursorId(), 2);

            assertThat(first.posts()).extracting(PostResDto.PostSummary::title).containsExactly("좋아요 1", "좋아요 0-4");
            assertThat(first.nextCursorLikeCount()).isZero();
            assertThat(second.posts()).extracting(PostResDto.PostSummary::title).containsExactly("좋아요 0-3", "좋아요 0-2");
            assertThat(last.posts()).extracting(PostResDto.PostSummary::title).containsExactly("좋아요 0-1");
            assertThat(last.hasNext()).isFalse();
            assertThat(last.nextCursorLikeCount()).isNull();
            assertThat(last.nextCursorId()).isNull();
        }

        @Test
        @DisplayName("커서 두 값 중 하나만 보내면 400이다")
        void rejectsHalfCursor() {
            assertThatThrownBy(() -> postService.getPopularPosts(null, null, 3, null, 20))
                    .extracting(PostServiceTest.this::errorCodeOf).isEqualTo(CommunityErrorCode.INVALID_POPULAR_CURSOR);
            assertThatThrownBy(() -> postService.getPopularPosts(null, null, null, 10L, 20))
                    .extracting(PostServiceTest.this::errorCodeOf).isEqualTo(CommunityErrorCode.INVALID_POPULAR_CURSOR);
        }

        @Test
        @DisplayName("집계 시작 시각은 그 주 월요일 0시다 (일요일 밤은 지난 월요일, 월요일 0시는 그 자신)")
        void startOfWeek() {
            // 2026-11-02는 월요일
            assertThat(PostService.startOfWeek(LocalDateTime.of(2026, 11, 5, 15, 30)))
                    .isEqualTo(LocalDateTime.of(2026, 11, 2, 0, 0));
            assertThat(PostService.startOfWeek(LocalDateTime.of(2026, 11, 8, 23, 59)))
                    .isEqualTo(LocalDateTime.of(2026, 11, 2, 0, 0));
            assertThat(PostService.startOfWeek(LocalDateTime.of(2026, 11, 2, 0, 0)))
                    .isEqualTo(LocalDateTime.of(2026, 11, 2, 0, 0));
        }
    }

    @Nested
    @DisplayName("작성")
    class Create {

        @Test
        @DisplayName("닉네임이 없으면 작성할 수 없다")
        void requiresNickname() {
            assertThatThrownBy(() -> postService.createPost(noNickname.getId(), new PostReqDto.CreateReq("005930", "제목", "내용")))
                    .isInstanceOf(CommunityException.class)
                    .extracting(PostServiceTest.this::errorCodeOf)
                    .isEqualTo(CommunityErrorCode.NICKNAME_REQUIRED);
        }

        @Test
        @DisplayName("없는 종목에는 작성할 수 없다")
        void rejectsUnknownStock() {
            assertThatThrownBy(() -> postService.createPost(writer.getId(), new PostReqDto.CreateReq("999999", "제목", "내용")))
                    .isInstanceOf(StockException.class);
        }

        @Test
        @DisplayName("작성하면 작성자 닉네임과 종목이 담긴다")
        void creates() {
            PostResDto.PostDetail res = postService.createPost(writer.getId(), new PostReqDto.CreateReq("005930", "제목", "내용"));

            assertThat(res.writerNickname()).isEqualTo("용감한개미");
            assertThat(res.stockName()).isEqualTo("삼성전자");
            assertThat(res.isMine()).isTrue();
            assertThat(res.isEdited()).isFalse();
        }
    }

    @Nested
    @DisplayName("수정/삭제")
    class UpdateAndDelete {

        @Test
        @DisplayName("작성자가 수정하면 내용이 바뀌고 수정됨으로 표시된다")
        void updates() throws InterruptedException {
            Long postId = write(samsung, "원래 제목");
            Thread.sleep(5); // createdAt과 updatedAt이 같은 시각으로 찍히지 않도록

            PostResDto.PostDetail res = postService.updatePost(writer.getId(), postId, new PostReqDto.UpdateReq("새 제목", "새 내용"));

            assertThat(res.title()).isEqualTo("새 제목");
            assertThat(res.isEdited()).isTrue();
        }

        @Test
        @DisplayName("다른 회원은 수정할 수 없다")
        void otherCannotUpdate() {
            Long postId = write(samsung, "제목");

            assertThatThrownBy(() -> postService.updatePost(other.getId(), postId, new PostReqDto.UpdateReq("가로채기", "내용")))
                    .isInstanceOf(CommunityException.class)
                    .extracting(PostServiceTest.this::errorCodeOf)
                    .isEqualTo(CommunityErrorCode.NOT_POST_WRITER);
        }

        @Test
        @DisplayName("다른 회원은 삭제할 수 없다")
        void otherCannotDelete() {
            Long postId = write(samsung, "제목");

            assertThatThrownBy(() -> postService.deletePost(other.getId(), postId))
                    .isInstanceOf(CommunityException.class)
                    .extracting(PostServiceTest.this::errorCodeOf)
                    .isEqualTo(CommunityErrorCode.NOT_POST_WRITER);
        }

        @Test
        @DisplayName("삭제하면 행은 남고, 이후 조회/수정/좋아요는 404다")
        void softDeletes() {
            Long postId = write(samsung, "제목");

            postService.deletePost(writer.getId(), postId);

            assertThat(em.find(Post.class, postId).isDeleted()).isTrue();
            assertThatThrownBy(() -> postService.getPost(null, postId))
                    .extracting(PostServiceTest.this::errorCodeOf).isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
            assertThatThrownBy(() -> postService.toggleLike(other.getId(), postId))
                    .extracting(PostServiceTest.this::errorCodeOf).isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("좋아요")
    class Like {

        @Test
        @DisplayName("누르면 좋아요가 되고, 다시 누르면 취소된다")
        void toggles() {
            Long postId = write(samsung, "제목");

            PostResDto.LikeRes on = postService.toggleLike(other.getId(), postId);
            PostResDto.LikeRes off = postService.toggleLike(other.getId(), postId);

            assertThat(on.liked()).isTrue();
            assertThat(on.likeCount()).isEqualTo(1);
            assertThat(off.liked()).isFalse();
            assertThat(off.likeCount()).isZero();
        }

        @Test
        @DisplayName("여러 회원의 좋아요가 모두 수에 반영된다")
        void countsAcrossMembers() {
            Long postId = write(samsung, "제목");

            postService.toggleLike(other.getId(), postId);
            PostResDto.LikeRes res = postService.toggleLike(writer.getId(), postId);

            assertThat(res.likeCount()).isEqualTo(2);
            assertThat(postService.getPost(writer.getId(), postId).likedByMe()).isTrue();
        }

        @Test
        @DisplayName("닉네임이 없어도 좋아요는 누를 수 있다")
        void noNicknameNeeded() {
            Long postId = write(samsung, "제목");

            assertThat(postService.toggleLike(noNickname.getId(), postId).liked()).isTrue();
        }
    }
}
