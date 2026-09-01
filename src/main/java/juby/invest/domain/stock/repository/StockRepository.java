package juby.invest.domain.stock.repository;

import juby.invest.domain.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, String> {

    Optional<Stock> findByStockCode(String stockCode);

    Optional<Stock> findByStockName(String stockName);

    @Query("select s.stockCode from Stock s")
    List<String> findAllStockCodes();
}
