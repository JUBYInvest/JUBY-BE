package juby.invest.domain.community.service;

import juby.invest.domain.community.converter.PostConverter;
import juby.invest.domain.community.dto.PostReqDto;
import juby.invest.domain.community.dto.PostResDto;
import juby.invest.domain.community.entity.Post;
import juby.invest.domain.community.entity.PostLike;
import juby.invest.domain.community.exception.CommunityException;
import juby.invest.domain.community.exception.code.CommunityErrorCode;
import juby.invest.domain.community.repository.PostLikeRepository;
import juby.invest.domain.community.repository.PostRepository;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.exception.StockException;
import juby.invest.domain.stock.exception.code.StockErrorCode;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final MemberRepository memberRepository;
    private final StockRepository stockRepository;

    /***
     * 함수 기능: 게시물 목록을 최신순으로 커서 조회한다.
     * @param viewerId 조회하는 회원 ID (비로그인이면 null)
     * @param stockCode 종목 코드 (null이면 전체 종목)
     * @param cursor 이전 페이지의 nextCursor (첫 페이지면 null)
     * @param size 조회 개수
     * @return 게시물 목록과 다음 커서
     */
    public PostResDto.PostList getPosts(Long viewerId, String stockCode, Long cursor, int size) {

        Long effectiveCursor = cursor == null ? Long.MAX_VALUE : cursor;

        // 한 개를 더 조회해서, 그 한 개가 있으면 다음 페이지가 있다고 판단한다. (count 쿼리 없이)
        PageRequest limit = PageRequest.ofSize(size + 1);
        List<Post> posts = stockCode == null
                ? postRepository.findFeed(effectiveCursor, limit)
                : postRepository.findFeedByStockCode(stockCode, effectiveCursor, limit);

        boolean hasNext = posts.size() > size;
        List<Post> page = hasNext ? posts.subList(0, size) : posts;

        Set<Long> likedPostIds = findLikedPostIds(viewerId, page);

        List<PostResDto.PostSummary> summaries = page.stream()
                .map(post -> PostConverter.toSummary(post, viewerId, likedPostIds.contains(post.getId())))
                .toList();

        return PostConverter.toPostList(summaries, hasNext);
    }

    /***
     * 함수 기능: 이번 주(월요일 0시부터 지금까지) 작성된 게시물을 좋아요 많은 순으로 커서 조회한다.
     * @param viewerId 조회하는 회원 ID (비로그인이면 null)
     * @param stockCode 종목 코드 (null이면 전체 종목)
     * @param cursorLikeCount 이전 페이지의 nextCursorLikeCount (첫 페이지면 null)
     * @param cursorId 이전 페이지의 nextCursorId (첫 페이지면 null)
     * @param size 조회 개수
     * @return 인기 게시물 목록과 다음 커서
     */
    public PostResDto.PopularPostList getPopularPosts(Long viewerId, String stockCode, Integer cursorLikeCount, Long cursorId, int size) {

        // 커서 두 값 중 하나만 오면 이어 볼 위치를 정할 수 없다.
        if ((cursorLikeCount == null) != (cursorId == null)) {
            throw new CommunityException(CommunityErrorCode.INVALID_POPULAR_CURSOR);
        }
        boolean firstPage = cursorId == null;
        int effectiveLikeCount = firstPage ? Integer.MAX_VALUE : cursorLikeCount;
        Long effectiveId = firstPage ? Long.MAX_VALUE : cursorId;

        LocalDateTime since = startOfWeek(LocalDateTime.now());

        PageRequest limit = PageRequest.ofSize(size + 1);
        List<Post> posts = stockCode == null
                ? postRepository.findPopular(since, effectiveLikeCount, effectiveId, limit)
                : postRepository.findPopularByStockCode(stockCode, since, effectiveLikeCount, effectiveId, limit);

        boolean hasNext = posts.size() > size;
        List<Post> page = hasNext ? posts.subList(0, size) : posts;

        Set<Long> likedPostIds = findLikedPostIds(viewerId, page);

        List<PostResDto.PostSummary> summaries = page.stream()
                .map(post -> PostConverter.toSummary(post, viewerId, likedPostIds.contains(post.getId())))
                .toList();

        return PostConverter.toPopularPostList(summaries, hasNext);
    }

    /***
     * 함수 기능: 주어진 시각이 속한 주의 월요일 0시를 구한다. (인기글 집계 시작 시각)
     *          createdAt이 JVM 기본 시간대(Dockerfile의 -Duser.timezone)로 저장되므로 같은 시간대로 계산한다.
     */
    static LocalDateTime startOfWeek(LocalDateTime now) {
        return now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
    }

    /***
     * 함수 기능: 게시물 상세를 조회한다.
     * @param viewerId 조회하는 회원 ID (비로그인이면 null)
     * @param postId 게시물 ID
     * @return 게시물 상세
     */
    public PostResDto.PostDetail getPost(Long viewerId, Long postId) {

        Post post = getActivePost(postId);
        boolean likedByMe = viewerId != null && postLikeRepository.existsByPostIdAndMemberId(postId, viewerId);

        return PostConverter.toDetail(post, viewerId, likedByMe);
    }

    /***
     * 함수 기능: 게시물을 작성한다. 닉네임을 설정한 회원만 작성할 수 있다.
     * @param memberId 작성자 ID
     * @param dto 종목 코드, 제목, 내용
     * @return 작성된 게시물 상세
     */
    @Transactional
    public PostResDto.PostDetail createPost(Long memberId, PostReqDto.CreateReq dto) {

        Member member = getActiveMember(memberId);

        // 프론트의 닉네임 팝업은 우회할 수 있으므로 서버에서 다시 막는다.
        if (member.getNickname() == null) {
            throw new CommunityException(CommunityErrorCode.NICKNAME_REQUIRED);
        }

        // 종목은 선택 사항이다. 고르지 않으면 종목 없는 글로 저장한다.
        Stock stock = dto.stockCode() == null ? null : stockRepository.findById(dto.stockCode())
                .orElseThrow(() -> new StockException(StockErrorCode.STOCK_NOT_FOUND));

        Post post = postRepository.save(Post.builder()
                .stock(stock)
                .member(member)
                .title(dto.title())
                .content(dto.content())
                .build());

        return PostConverter.toDetail(post, memberId, false);
    }

    /***
     * 함수 기능: 게시물을 수정한다. 작성자 본인만 수정할 수 있다.
     * @param memberId 요청한 회원 ID
     * @param postId 게시물 ID
     * @param dto 제목, 내용
     * @return 수정된 게시물 상세
     */
    @Transactional
    public PostResDto.PostDetail updatePost(Long memberId, Long postId, PostReqDto.UpdateReq dto) {

        Post post = getWritersPost(memberId, postId);
        post.update(dto.title(), dto.content());

        // updatedAt은 @PreUpdate에서 flush 시점에 채워지므로, 응답에 반영되도록 먼저 flush한다.
        postRepository.flush();

        boolean likedByMe = postLikeRepository.existsByPostIdAndMemberId(postId, memberId);
        return PostConverter.toDetail(post, memberId, likedByMe);
    }

    /***
     * 함수 기능: 게시물을 삭제(soft delete)한다. 작성자 본인만 삭제할 수 있다.
     * @param memberId 요청한 회원 ID
     * @param postId 게시물 ID
     */
    @Transactional
    public void deletePost(Long memberId, Long postId) {

        Post post = getWritersPost(memberId, postId);
        post.delete();
    }

    /***
     * 함수 기능: 게시물 좋아요를 누르거나 취소한다. (토글)
     * @param memberId 요청한 회원 ID
     * @param postId 게시물 ID
     * @return 좋아요 여부와 반영된 좋아요 수
     */
    @Transactional
    public PostResDto.LikeRes toggleLike(Long memberId, Long postId) {

        Member member = getActiveMember(memberId);
        Post post = getActivePost(postId);

        // 먼저 지워 본다. 실제로 지운 경우에만 수를 줄여, 동시 요청이 와도 좋아요 수가 음수로 틀어지지 않는다.
        boolean liked;
        if (postLikeRepository.deleteByPostIdAndMemberId(postId, memberId) > 0) {
            postRepository.decreaseLikeCount(postId);
            liked = false;
        } else {
            try {
                postLikeRepository.saveAndFlush(PostLike.builder().post(post).member(member).build());
            } catch (DataIntegrityViolationException e) {
                // 같은 회원의 요청이 동시에 들어와 다른 요청이 먼저 좋아요를 저장한 경우
                throw new CommunityException(CommunityErrorCode.LIKE_CONFLICT);
            }
            postRepository.increaseLikeCount(postId);
            liked = true;
        }

        return PostResDto.LikeRes.builder()
                .postId(postId)
                .liked(liked)
                .likeCount(postRepository.findLikeCountById(postId))
                .build();
    }

    private Set<Long> findLikedPostIds(Long viewerId, List<Post> posts) {
        if (viewerId == null || posts.isEmpty()) {
            return Set.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        return Set.copyOf(postLikeRepository.findLikedPostIds(viewerId, postIds));
    }

    private Post getActivePost(Long postId) {
        return postRepository.findActiveById(postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
    }

    private Post getWritersPost(Long memberId, Long postId) {
        Post post = getActivePost(postId);
        if (!post.isWrittenBy(memberId)) {
            throw new CommunityException(CommunityErrorCode.NOT_POST_WRITER);
        }
        return post;
    }

    private Member getActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
