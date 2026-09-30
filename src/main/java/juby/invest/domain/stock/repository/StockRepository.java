package juby.invest.domain.stock.repository;

import juby.invest.domain.stock.entity.Stock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, String> {

    Optional<Stock> findByStockCode(String stockCode);

    Optional<Stock> findByStockName(String stockName);

    @Query("select s.stockCode from Stock s")
    List<String> findAllStockCodes();

    // 종목명에 keyword가 포함된 종목을 검색하되, keyword로 시작하는 종목을 먼저 보여준다.
    // keyword는 LIKE 특수문자(%, _)를 '!'로 이스케이프한 뒤 전달한다. (백슬래시는 MySQL 문자열 리터럴에서 해석이 달라 사용하지 않음)
    @Query("select s from Stock s " +
            "where s.stockName like concat('%', :keyword, '%') escape '!' " +
            "order by case when s.stockName like concat(:keyword, '%') escape '!' then 0 else 1 end, s.stockName")
    List<Stock> searchByStockName(@Param("keyword") String keyword, Pageable pageable);
}
