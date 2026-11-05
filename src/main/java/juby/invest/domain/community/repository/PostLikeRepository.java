package juby.invest.domain.community.repository;

import juby.invest.domain.community.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByPostIdAndMemberId(Long postId, Long memberId);

    // 지운 행 수를 돌려준다. 동시 요청에서 실제로 지운 쪽만 좋아요 수를 줄이기 위해 쓴다.
    @Modifying
    @Query("delete from PostLike pl where pl.post.id = :postId and pl.member.id = :memberId")
    int deleteByPostIdAndMemberId(@Param("postId") Long postId, @Param("memberId") Long memberId);

    // 목록 한 페이지에서 내가 좋아요한 게시물 id만 한 번에 조회한다. (게시물마다 조회하면 N+1)
    @Query("select pl.post.id from PostLike pl where pl.member.id = :memberId and pl.post.id in :postIds")
    List<Long> findLikedPostIds(@Param("memberId") Long memberId, @Param("postIds") Collection<Long> postIds);
}
