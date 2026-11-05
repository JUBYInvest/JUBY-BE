package juby.invest.domain.community.repository;

import juby.invest.domain.community.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    /***
     * 함수 기능: 전체 종목 게시물을 최신순으로 커서 조회한다.
     *          "stockCode가 null이면 전체" 같은 조건을 한 쿼리에 넣으면 인덱스를 못 타므로 종목별 조회와 쿼리를 나눈다.
     *          종목 없는 글도 나와야 하므로 종목은 left join 한다.
     * @param cursor 이 id보다 작은 게시물만 조회 (첫 페이지는 Long.MAX_VALUE)
     * @param pageable 조회 개수 (다음 페이지 여부 판단을 위해 size + 1개를 요청한다)
     */
    @Query("""
        select p from Post p
        left join fetch p.stock
        join fetch p.member
        where p.deletedAt is null and p.id < :cursor
        order by p.id desc
    """)
    List<Post> findFeed(@Param("cursor") Long cursor, Pageable pageable);

    // 특정 종목 게시물을 최신순으로 커서 조회한다. (stock_code, id) 인덱스를 탄다.
    @Query("""
        select p from Post p
        join fetch p.stock s
        join fetch p.member
        where s.stockCode = :stockCode and p.deletedAt is null and p.id < :cursor
        order by p.id desc
    """)
    List<Post> findFeedByStockCode(@Param("stockCode") String stockCode, @Param("cursor") Long cursor, Pageable pageable);

    /***
     * 함수 기능: since 이후 작성된 게시물을 좋아요 많은 순으로 커서 조회한다. 좋아요 수가 같으면 최신 글이 먼저다.
     *          좋아요 수는 여러 글이 같을 수 있어 id 하나로는 이어 볼 위치를 정할 수 없으므로 (좋아요 수, id)를 커서로 쓴다.
     * @param since 이 시각 이후 작성된 글만 (이번 주 월요일 0시)
     * @param cursorLikeCount 이전 페이지 마지막 글의 좋아요 수 (첫 페이지는 Integer.MAX_VALUE)
     * @param cursorId 이전 페이지 마지막 글의 id (첫 페이지는 Long.MAX_VALUE)
     */
    @Query("""
        select p from Post p
        left join fetch p.stock
        join fetch p.member
        where p.deletedAt is null and p.createdAt >= :since
          and (p.likeCount < :cursorLikeCount or (p.likeCount = :cursorLikeCount and p.id < :cursorId))
        order by p.likeCount desc, p.id desc
    """)
    List<Post> findPopular(@Param("since") LocalDateTime since,
                           @Param("cursorLikeCount") int cursorLikeCount,
                           @Param("cursorId") Long cursorId,
                           Pageable pageable);

    @Query("""
        select p from Post p
        join fetch p.stock s
        join fetch p.member
        where s.stockCode = :stockCode and p.deletedAt is null and p.createdAt >= :since
          and (p.likeCount < :cursorLikeCount or (p.likeCount = :cursorLikeCount and p.id < :cursorId))
        order by p.likeCount desc, p.id desc
    """)
    List<Post> findPopularByStockCode(@Param("stockCode") String stockCode,
                                      @Param("since") LocalDateTime since,
                                      @Param("cursorLikeCount") int cursorLikeCount,
                                      @Param("cursorId") Long cursorId,
                                      Pageable pageable);

    // 삭제되지 않은 전체 게시물 수 (종목 없는 글 포함)
    long countByDeletedAtIsNull();

    // 종목별 게시물 수를 많은 순으로 조회한다. 종목 없는 글은 빠진다.
    @Query("""
        select s.stockCode as stockCode, s.stockName as stockName, count(p) as postCount
        from Post p
        join p.stock s
        where p.deletedAt is null
        group by s.stockCode, s.stockName
        order by count(p) desc, s.stockCode
    """)
    List<StockPostCount> countByStock(Pageable pageable);

    // 삭제되지 않은 게시물을 작성자, 종목과 함께 조회한다.
    @Query("""
        select p from Post p
        left join fetch p.stock
        join fetch p.member
        where p.id = :id and p.deletedAt is null
    """)
    Optional<Post> findActiveById(@Param("id") Long id);

    // 좋아요 수 증감. 엔티티 값을 읽어 +1 후 저장하면 동시 요청 시 서로 덮어써 숫자가 틀어지므로 DB에서 바로 계산한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :id")
    void increaseLikeCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount - 1 where p.id = :id and p.likeCount > 0")
    void decreaseLikeCount(@Param("id") Long id);

    @Query("select p.likeCount from Post p where p.id = :id")
    int findLikeCountById(@Param("id") Long id);

    // 댓글 수 증감. 좋아요 수와 같은 이유로 DB에서 바로 계산한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :id")
    void increaseCommentCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.commentCount = p.commentCount - 1 where p.id = :id and p.commentCount > 0")
    void decreaseCommentCount(@Param("id") Long id);
}
