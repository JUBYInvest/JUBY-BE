package juby.invest.repository;

import juby.invest.domain.DailyPrice;
import juby.invest.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {
    boolean existsByStock(Stock stock);

    List<DailyPrice> findByStockStockCodeOrderByDateAsc(String stockCode);
}
