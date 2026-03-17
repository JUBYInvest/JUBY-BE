package juby.invest.repository;

import juby.invest.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockRepository extends JpaRepository<Stock, String> {

    @Query("select s.stockCode from Stock s")
    List<String> findAllStockCodes();
}
