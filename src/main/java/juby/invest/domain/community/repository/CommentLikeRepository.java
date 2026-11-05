package juby.invest.domain.community.repository;

import juby.invest.domain.community.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    // 지운 행 수를 돌려준다. 동시 요청에서 실제로 지운 쪽만 좋아요 수를 줄이기 위해 쓴다.
    @Modifying
    @Query("delete from CommentLike cl where cl.comment.id = :commentId and cl.member.id = :memberId")
    int deleteByCommentIdAndMemberId(@Param("commentId") Long commentId, @Param("memberId") Long memberId);

    // 댓글 한 페이지에서 내가 좋아요한 댓글 id만 한 번에 조회한다. (댓글마다 조회하면 N+1)
    @Query("select cl.comment.id from CommentLike cl where cl.member.id = :memberId and cl.comment.id in :commentIds")
    List<Long> findLikedCommentIds(@Param("memberId") Long memberId, @Param("commentIds") Collection<Long> commentIds);
}
