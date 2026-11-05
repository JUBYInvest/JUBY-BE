package juby.invest.domain.community.repository;

import juby.invest.domain.community.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /***
     * 함수 기능: 게시물의 댓글을 작성순(오래된 순)으로 커서 조회한다. (post_id, id) 인덱스를 탄다.
     * @param postId 게시물 ID
     * @param cursor 이 id보다 큰 댓글만 조회 (첫 페이지는 0)
     * @param pageable 조회 개수 (다음 페이지 여부 판단을 위해 size + 1개를 요청한다)
     */
    @Query("""
        select c from Comment c
        join fetch c.member
        where c.post.id = :postId and c.deletedAt is null and c.id > :cursor
        order by c.id asc
    """)
    List<Comment> findByPostId(@Param("postId") Long postId, @Param("cursor") Long cursor, Pageable pageable);

    // 삭제되지 않은 댓글을 조회한다. 게시물이 삭제됐으면 댓글도 함께 숨겨진 것으로 본다.
    @Query("""
        select c from Comment c
        join fetch c.member
        join fetch c.post p
        where c.id = :id and c.deletedAt is null and p.deletedAt is null
    """)
    Optional<Comment> findActiveById(@Param("id") Long id);

    // 좋아요 수 증감. 동시 요청에도 숫자가 틀어지지 않도록 DB에서 바로 계산한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Comment c set c.likeCount = c.likeCount + 1 where c.id = :id")
    void increaseLikeCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Comment c set c.likeCount = c.likeCount - 1 where c.id = :id and c.likeCount > 0")
    void decreaseLikeCount(@Param("id") Long id);

    @Query("select c.likeCount from Comment c where c.id = :id")
    int findLikeCountById(@Param("id") Long id);
}
