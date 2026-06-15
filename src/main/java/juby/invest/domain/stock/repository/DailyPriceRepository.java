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

    boolean existsByStock(Stock stock);

    List<DailyPrice> findByStock_StockCodeOrderByDateAsc(String stockCode);

    List<DailyPrice> findByStock_StockCodeAndDateBetweenOrderByDateAsc(
            String stockCode,
            LocalDate startDate,
            LocalDate endDate
    );

    String stock(Stock stock);

    boolean findByStock(Stock stock);

    List<DailyPrice> findAllByStock(Stock stock);

    boolean existsByDate(LocalDate date);

    boolean existsByStock_StockCodeAndDate(String stockStockCode, LocalDate date);
}
