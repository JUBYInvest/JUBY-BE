package juby.invest.domain.community.service;

import juby.invest.domain.community.converter.CommentConverter;
import juby.invest.domain.community.dto.CommentReqDto;
import juby.invest.domain.community.dto.CommentResDto;
import juby.invest.domain.community.entity.Comment;
import juby.invest.domain.community.entity.CommentLike;
import juby.invest.domain.community.entity.Post;
import juby.invest.domain.community.exception.CommunityException;
import juby.invest.domain.community.exception.code.CommunityErrorCode;
import juby.invest.domain.community.repository.CommentLikeRepository;
import juby.invest.domain.community.repository.CommentRepository;
import juby.invest.domain.community.repository.PostRepository;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    /***
     * 함수 기능: 게시물의 댓글을 작성순(오래된 순)으로 커서 조회한다.
     * @param viewerId 조회하는 회원 ID (비로그인이면 null)
     * @param postId 게시물 ID
     * @param cursor 이전 페이지의 nextCursor (첫 페이지면 null)
     * @param size 조회 개수
     * @return 댓글 목록과 다음 커서
     */
    public CommentResDto.CommentList getComments(Long viewerId, Long postId, Long cursor, int size) {

        // 삭제된 게시물의 댓글은 게시물과 함께 숨긴다.
        getActivePost(postId);

        Long effectiveCursor = cursor == null ? 0L : cursor;

        // 한 개를 더 조회해서, 그 한 개가 있으면 다음 페이지가 있다고 판단한다. (count 쿼리 없이)
        List<Comment> comments = commentRepository.findByPostId(postId, effectiveCursor, PageRequest.ofSize(size + 1));

        boolean hasNext = comments.size() > size;
        List<Comment> page = hasNext ? comments.subList(0, size) : comments;

        Set<Long> likedCommentIds = findLikedCommentIds(viewerId, page);

        List<CommentResDto.CommentInfo> infos = page.stream()
                .map(comment -> CommentConverter.toInfo(comment, postId, viewerId, likedCommentIds.contains(comment.getId())))
                .toList();

        return CommentConverter.toCommentList(infos, hasNext);
    }

    /***
     * 함수 기능: 댓글을 작성한다. 닉네임을 설정한 회원만 작성할 수 있다.
     * @param memberId 작성자 ID
     * @param postId 게시물 ID
     * @param dto 댓글 내용
     * @return 작성된 댓글
     */
    @Transactional
    public CommentResDto.CommentInfo createComment(Long memberId, Long postId, CommentReqDto.ContentReq dto) {

        Member member = getActiveMember(memberId);

        // 프론트의 닉네임 팝업은 우회할 수 있으므로 서버에서 다시 막는다.
        if (member.getNickname() == null) {
            throw new CommunityException(CommunityErrorCode.NICKNAME_REQUIRED);
        }

        Post post = getActivePost(postId);

        Comment comment = commentRepository.save(Comment.builder()
                .post(post)
                .member(member)
                .content(dto.content())
                .build());

        postRepository.increaseCommentCount(postId);

        return CommentConverter.toInfo(comment, postId, memberId, false);
    }

    /***
     * 함수 기능: 댓글을 수정한다. 작성자 본인만 수정할 수 있다.
     * @param memberId 요청한 회원 ID
     * @param commentId 댓글 ID
     * @param dto 댓글 내용
     * @return 수정된 댓글
     */
    @Transactional
    public CommentResDto.CommentInfo updateComment(Long memberId, Long commentId, CommentReqDto.ContentReq dto) {

        Comment comment = getWritersComment(memberId, commentId);
        comment.update(dto.content());

        // updatedAt은 @PreUpdate에서 flush 시점에 채워지므로, 응답에 반영되도록 먼저 flush한다.
        commentRepository.flush();

        boolean likedByMe = !findLikedCommentIds(memberId, List.of(comment)).isEmpty();
        return CommentConverter.toInfo(comment, comment.getPost().getId(), memberId, likedByMe);
    }

    /***
     * 함수 기능: 댓글을 삭제(soft delete)한다. 작성자 본인만 삭제할 수 있다.
     * @param memberId 요청한 회원 ID
     * @param commentId 댓글 ID
     */
    @Transactional
    public void deleteComment(Long memberId, Long commentId) {

        Comment comment = getWritersComment(memberId, commentId);
        comment.delete();

        postRepository.decreaseCommentCount(comment.getPost().getId());
    }

    /***
     * 함수 기능: 댓글 좋아요를 누르거나 취소한다. (토글)
     * @param memberId 요청한 회원 ID
     * @param commentId 댓글 ID
     * @return 좋아요 여부와 반영된 좋아요 수
     */
    @Transactional
    public CommentResDto.LikeRes toggleLike(Long memberId, Long commentId) {

        Member member = getActiveMember(memberId);
        Comment comment = getActiveComment(commentId);

        // 먼저 지워 본다. 실제로 지운 경우에만 수를 줄여, 동시 요청이 와도 좋아요 수가 틀어지지 않는다.
        boolean liked;
        if (commentLikeRepository.deleteByCommentIdAndMemberId(commentId, memberId) > 0) {
            commentRepository.decreaseLikeCount(commentId);
            liked = false;
        } else {
            try {
                commentLikeRepository.saveAndFlush(CommentLike.builder().comment(comment).member(member).build());
            } catch (DataIntegrityViolationException e) {
                // 같은 회원의 요청이 동시에 들어와 다른 요청이 먼저 좋아요를 저장한 경우
                throw new CommunityException(CommunityErrorCode.LIKE_CONFLICT);
            }
            commentRepository.increaseLikeCount(commentId);
            liked = true;
        }

        return CommentResDto.LikeRes.builder()
                .commentId(commentId)
                .liked(liked)
                .likeCount(commentRepository.findLikeCountById(commentId))
                .build();
    }

    private Set<Long> findLikedCommentIds(Long viewerId, List<Comment> comments) {
        if (viewerId == null || comments.isEmpty()) {
            return Set.of();
        }
        List<Long> commentIds = comments.stream().map(Comment::getId).toList();
        return Set.copyOf(commentLikeRepository.findLikedCommentIds(viewerId, commentIds));
    }

    private Post getActivePost(Long postId) {
        return postRepository.findActiveById(postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
    }

    private Comment getActiveComment(Long commentId) {
        return commentRepository.findActiveById(commentId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.COMMENT_NOT_FOUND));
    }

    private Comment getWritersComment(Long memberId, Long commentId) {
        Comment comment = getActiveComment(commentId);
        if (!comment.isWrittenBy(memberId)) {
            throw new CommunityException(CommunityErrorCode.NOT_COMMENT_WRITER);
        }
        return comment;
    }

    private Member getActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
