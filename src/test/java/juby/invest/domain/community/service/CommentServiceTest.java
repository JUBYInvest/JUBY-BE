package juby.invest.domain.community.service;

import juby.invest.domain.community.dto.CommentReqDto;
import juby.invest.domain.community.dto.CommentResDto;
import juby.invest.domain.community.dto.PostReqDto;
import juby.invest.domain.community.entity.Comment;
import juby.invest.domain.community.exception.CommunityException;
import juby.invest.domain.community.exception.code.CommunityErrorCode;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.stock.entity.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/***
 * 댓글 수(post.comment_count)와 좋아요 수는 벌크 UPDATE로 증감하므로, 쿼리가 실제로 반영되는지 H2 위에서 확인한다.
 */
@DataJpaTest
@Import({CommentService.class, PostService.class})
@DisplayName("CommentService")
class CommentServiceTest {

    @Autowired private TestEntityManager em;
    @Autowired private CommentService commentService;
    @Autowired private PostService postService;

    private Member writer;
    private Member other;
    private Member noNickname;
    private Long postId;

    @BeforeEach
    void setUp() {
        writer = em.persist(Member.builder().name("작성자").nickname("용감한개미").build());
        other = em.persist(Member.builder().name("다른회원").nickname("신중한개미").build());
        noNickname = em.persist(Member.builder().name("닉네임없음").build());
        Stock samsung = em.persist(Stock.builder().stockCode("005930").stockName("삼성전자").build());
        em.flush();

        postId = postService.createPost(writer.getId(), new PostReqDto.CreateReq(samsung.getStockCode(), "제목", "내용")).postId();
    }

    private Long comment(Member member, String content) {
        return commentService.createComment(member.getId(), postId, new CommentReqDto.ContentReq(content)).commentId();
    }

    private int commentCountOfPost() {
        return postService.getPost(null, postId).commentCount();
    }

    private CommunityErrorCode errorCodeOf(Throwable e) {
        return (CommunityErrorCode) ((CommunityException) e).getErrorCode();
    }

    @Nested
    @DisplayName("목록 조회")
    class GetComments {

        @Test
        @DisplayName("댓글을 작성순(오래된 순)으로 반환한다")
        void oldestFirst() {
            comment(writer, "첫 댓글");
            comment(other, "둘째 댓글");

            List<CommentResDto.CommentInfo> comments = commentService.getComments(null, postId, null, 20).comments();

            assertThat(comments).extracting(CommentResDto.CommentInfo::content).containsExactly("첫 댓글", "둘째 댓글");
            assertThat(comments).extracting(CommentResDto.CommentInfo::writerNickname).containsExactly("용감한개미", "신중한개미");
        }

        @Test
        @DisplayName("nextCursor로 다음 페이지를 이어서 조회하면 중복/누락이 없다")
        void cursorPaging() {
            for (int i = 1; i <= 5; i++) {
                comment(writer, "댓글" + i);
            }

            CommentResDto.CommentList first = commentService.getComments(null, postId, null, 2);
            CommentResDto.CommentList second = commentService.getComments(null, postId, first.nextCursor(), 2);
            CommentResDto.CommentList last = commentService.getComments(null, postId, second.nextCursor(), 2);

            assertThat(first.comments()).extracting(CommentResDto.CommentInfo::content).containsExactly("댓글1", "댓글2");
            assertThat(second.comments()).extracting(CommentResDto.CommentInfo::content).containsExactly("댓글3", "댓글4");
            assertThat(last.comments()).extracting(CommentResDto.CommentInfo::content).containsExactly("댓글5");
            assertThat(last.hasNext()).isFalse();
            assertThat(last.nextCursor()).isNull();
        }

        @Test
        @DisplayName("로그인 사용자에게는 내 댓글 여부와 내 좋아요 여부를 표시한다")
        void viewerFlags() {
            Long liked = comment(writer, "좋아요 받을 댓글");
            comment(other, "내 댓글");
            commentService.toggleLike(other.getId(), liked);

            List<CommentResDto.CommentInfo> asOther = commentService.getComments(other.getId(), postId, null, 20).comments();

            assertThat(asOther).extracting(CommentResDto.CommentInfo::likedByMe).containsExactly(true, false);
            assertThat(asOther).extracting(CommentResDto.CommentInfo::isMine).containsExactly(false, true);
            assertThat(commentService.getComments(null, postId, null, 20).comments())
                    .extracting(CommentResDto.CommentInfo::likedByMe).containsOnly(false);
        }

        @Test
        @DisplayName("게시물이 삭제되면 댓글 목록도 404다")
        void hiddenWithDeletedPost() {
            comment(writer, "댓글");
            postService.deletePost(writer.getId(), postId);

            assertThatThrownBy(() -> commentService.getComments(null, postId, null, 20))
                    .extracting(CommentServiceTest.this::errorCodeOf).isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("작성")
    class Create {

        @Test
        @DisplayName("작성하면 게시물의 댓글 수가 늘어난다")
        void increasesCommentCount() {
            comment(writer, "하나");
            comment(other, "둘");

            assertThat(commentCountOfPost()).isEqualTo(2);
        }

        @Test
        @DisplayName("닉네임이 없으면 작성할 수 없다")
        void requiresNickname() {
            assertThatThrownBy(() -> comment(noNickname, "댓글"))
                    .isInstanceOf(CommunityException.class)
                    .extracting(CommentServiceTest.this::errorCodeOf)
                    .isEqualTo(CommunityErrorCode.NICKNAME_REQUIRED);
            assertThat(commentCountOfPost()).isZero();
        }

        @Test
        @DisplayName("삭제된 게시물에는 작성할 수 없다")
        void rejectsDeletedPost() {
            postService.deletePost(writer.getId(), postId);

            assertThatThrownBy(() -> comment(other, "댓글"))
                    .extracting(CommentServiceTest.this::errorCodeOf).isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("수정/삭제")
    class UpdateAndDelete {

        @Test
        @DisplayName("작성자가 수정하면 내용이 바뀌고 수정됨으로 표시된다")
        void updates() throws InterruptedException {
            Long commentId = comment(writer, "원래 댓글");
            Thread.sleep(5); // createdAt과 updatedAt이 같은 시각으로 찍히지 않도록

            CommentResDto.CommentInfo res = commentService.updateComment(writer.getId(), commentId, new CommentReqDto.ContentReq("고친 댓글"));

            assertThat(res.content()).isEqualTo("고친 댓글");
            assertThat(res.isEdited()).isTrue();
            assertThat(res.postId()).isEqualTo(postId);
        }

        @Test
        @DisplayName("다른 회원은 수정할 수 없다")
        void otherCannotUpdate() {
            Long commentId = comment(writer, "댓글");

            assertThatThrownBy(() -> commentService.updateComment(other.getId(), commentId, new CommentReqDto.ContentReq("가로채기")))
                    .extracting(CommentServiceTest.this::errorCodeOf).isEqualTo(CommunityErrorCode.NOT_COMMENT_WRITER);
        }

        @Test
        @DisplayName("다른 회원은 삭제할 수 없다")
        void otherCannotDelete() {
            Long commentId = comment(writer, "댓글");

            assertThatThrownBy(() -> commentService.deleteComment(other.getId(), commentId))
                    .extracting(CommentServiceTest.this::errorCodeOf).isEqualTo(CommunityErrorCode.NOT_COMMENT_WRITER);
            assertThat(commentCountOfPost()).isEqualTo(1);
        }

        @Test
        @DisplayName("삭제하면 행은 남고, 목록에서 빠지며 댓글 수가 줄어든다")
        void softDeletes() {
            Long deleted = comment(writer, "지울 댓글");
            comment(writer, "남길 댓글");

            commentService.deleteComment(writer.getId(), deleted);

            assertThat(em.find(Comment.class, deleted).isDeleted()).isTrue();
            assertThat(commentService.getComments(null, postId, null, 20).comments())
                    .extracting(CommentResDto.CommentInfo::content).containsExactly("남길 댓글");
            assertThat(commentCountOfPost()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 삭제한 댓글을 다시 삭제하면 404이고 댓글 수는 한 번만 줄어든다")
        void deleteTwice() {
            Long commentId = comment(writer, "댓글");
            commentService.deleteComment(writer.getId(), commentId);

            assertThatThrownBy(() -> commentService.deleteComment(writer.getId(), commentId))
                    .extracting(CommentServiceTest.this::errorCodeOf).isEqualTo(CommunityErrorCode.COMMENT_NOT_FOUND);
            assertThat(commentCountOfPost()).isZero();
        }

        @Test
        @DisplayName("게시물이 삭제되면 그 댓글은 수정/좋아요할 수 없다")
        void blockedWithDeletedPost() {
            Long commentId = comment(writer, "댓글");
            postService.deletePost(writer.getId(), postId);

            assertThatThrownBy(() -> commentService.updateComment(writer.getId(), commentId, new CommentReqDto.ContentReq("수정")))
                    .extracting(CommentServiceTest.this::errorCodeOf).isEqualTo(CommunityErrorCode.COMMENT_NOT_FOUND);
            assertThatThrownBy(() -> commentService.toggleLike(other.getId(), commentId))
                    .extracting(CommentServiceTest.this::errorCodeOf).isEqualTo(CommunityErrorCode.COMMENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("좋아요")
    class Like {

        @Test
        @DisplayName("누르면 좋아요가 되고, 다시 누르면 취소된다")
        void toggles() {
            Long commentId = comment(writer, "댓글");

            CommentResDto.LikeRes on = commentService.toggleLike(other.getId(), commentId);
            CommentResDto.LikeRes off = commentService.toggleLike(other.getId(), commentId);

            assertThat(on.liked()).isTrue();
            assertThat(on.likeCount()).isEqualTo(1);
            assertThat(off.liked()).isFalse();
            assertThat(off.likeCount()).isZero();
        }

        @Test
        @DisplayName("여러 회원의 좋아요가 모두 수에 반영되고, 닉네임이 없어도 누를 수 있다")
        void countsAcrossMembers() {
            Long commentId = comment(writer, "댓글");

            commentService.toggleLike(other.getId(), commentId);
            CommentResDto.LikeRes res = commentService.toggleLike(noNickname.getId(), commentId);

            assertThat(res.likeCount()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("탈퇴한 회원의 댓글은 작성자가 '탈퇴한 회원'으로 표시된다")
    void withdrawnWriter() {
        comment(other, "댓글");
        Member withdrawn = em.find(Member.class, other.getId());
        withdrawn.withdraw();
        em.flush();
        em.clear();

        assertThat(commentService.getComments(null, postId, null, 20).comments().getFirst().writerNickname())
                .isEqualTo("탈퇴한 회원");
    }
}
