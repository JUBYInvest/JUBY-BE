package juby.invest.domain.member.repository;

import juby.invest.domain.member.entity.LikeStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LikeStockRepository extends JpaRepository<LikeStock, Long> {

    @Query("""
        select ls
        from LikeStock ls
        join fetch ls.stock s
        where ls.stock.stockCode = :stockCode
            and ls.member.id = :memberId
    """)
    Optional<LikeStock> findByMemberIdAndStockCodeWithStock(Long memberId, String stockCode);

    @Query("""
        select ls
        from LikeStock ls
        join fetch ls.stock s
        where ls.member.id = :memberId
    """)
    List<LikeStock> findAllByMemberIdWithStock(Long memberId);

    @Query("""
        select ls.stock.stockCode
        from LikeStock ls
        where ls.member.id = :memberId
    """)
    Set<String> findStockCodesByMemberId(Long memberId);
}