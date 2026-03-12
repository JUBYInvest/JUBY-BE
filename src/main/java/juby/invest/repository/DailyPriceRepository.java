package juby.invest.repository;

import juby.invest.domain.DailyPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {
}
