package juby.invest.domain.stock.repository;

import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {

    boolean existsByDate(LocalDate date);

    boolean existsByStock_StockCodeAndDate(String stockStockCode, LocalDate date);

    @Query("SELECT MAX(dp.date) FROM DailyPrice dp WHERE dp.stock = :stock")
    LocalDate findMaxDateByStock(Stock stock);

    List<DailyPrice> findAllByStockAndDateGreaterThanEqualOrderByDateAsc(Stock stock, LocalDate dateIsGreaterThan);

    List<DailyPrice> findByStockAndDateBetweenOrderByDateAsc(Stock stock, LocalDate startDate, LocalDate endDate);
}
