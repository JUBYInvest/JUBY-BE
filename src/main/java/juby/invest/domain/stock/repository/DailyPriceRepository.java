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

    List<DailyPrice> findByStock_StockCodeAndDateBetweenOrderByDateAsc(
            String stockCode,
            LocalDate startDate,
            LocalDate endDate
    );

    boolean existsByDate(LocalDate date);

    @Query("SELECT MAX(dp.date) FROM DailyPrice dp WHERE dp.stock = :stock")
    LocalDate findMaxDateByStock(Stock stock);

    boolean existsByStockAndDate(Stock stock, LocalDate today);
}
