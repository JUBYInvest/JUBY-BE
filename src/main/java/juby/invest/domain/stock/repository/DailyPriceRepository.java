package juby.invest.domain.stock.repository;

import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {

    boolean existsByDate(LocalDate date);

    @Query("SELECT MAX(dp.date) FROM DailyPrice dp WHERE dp.stock = :stock")
    LocalDate findMaxDateByStock(Stock stock);

    boolean existsByStockAndDate(Stock stock, LocalDate today);

    List<DailyPrice> findAllByStockAndDateGreaterThanEqualOrderByDateAsc(Stock stock, LocalDate dateIsGreaterThan);

    List<DailyPrice> findByStockAndDateBetweenOrderByDateAsc(Stock stock, LocalDate startDate, LocalDate endDate);

    @Query("""
        select max(dp.date)
        from DailyPrice dp
        """)
    LocalDate findMaxDate();

    @Query("""
        select max(dp.date)
        from DailyPrice dp
        where dp.date < :baseDate
        """)
    LocalDate findMaxDateBefore(LocalDate baseDate);

    @Query("""
        select dp
        from DailyPrice dp
        join fetch dp.stock
        where dp.date = :baseDate
        """)
    List<DailyPrice> findAllByDateWithStock(LocalDate baseDate);
}
