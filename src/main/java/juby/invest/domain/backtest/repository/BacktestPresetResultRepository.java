package juby.invest.domain.backtest.repository;

import juby.invest.domain.backtest.entity.BacktestPresetResult;
import juby.invest.domain.backtest.enums.BacktestPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BacktestPresetResultRepository extends JpaRepository<BacktestPresetResult, Long> {

    Optional<BacktestPresetResult> findByStock_StockCodeAndInvestTypeAndPeriod(
            String stockCode, int investType, BacktestPeriod period);

    // 해당 투자성향으로 실제 DB에 적재된(=배치 계산이 성공한) 기간 프리셋 목록. 종목 무관 global 기준.
    @Query("SELECT DISTINCT bpr.period FROM BacktestPresetResult bpr WHERE bpr.investType = :investType")
    List<BacktestPeriod> findDistinctPeriodsByInvestType(@Param("investType") int investType);
}
