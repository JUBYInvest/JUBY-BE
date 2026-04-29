package juby.invest.domain.stock.repository;

import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {
    boolean existsByStock(Stock stock);

    List<DailyPrice> findByStockStockCodeOrderByDateAsc(String stockCode);
}
