package juby.invest.repository;

import juby.invest.domain.DailyPrice;
import juby.invest.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {
    double findByDateBefore(LocalDate dateBefore);

    boolean existsByStockAndDate(Stock stock,LocalDate date);
}
