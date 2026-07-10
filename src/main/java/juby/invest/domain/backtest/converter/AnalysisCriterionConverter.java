package juby.invest.domain.backtest.converter;

import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.indicator.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.SharpeRatioCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.helpers.StandardDeviationCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class AnalysisCriterionConverter {

    private final EffectIndicator effectIndicator;
    private final GrowthIndicator growthIndicator;
    private final ProfitIndicator profitIndicator;
    private final StableIndicator stableIndicator;
    private final ScoreCalculator scoreCalculator;

    public BacktestResDto.QuantScoringResponse converter(
            String stockCode, int investType, BarSeries series, TradingRecord record){

        BacktestResDto.QuantScoringResponse.Stable stable = stableIndicator.indicate(series, record);
        BacktestResDto.QuantScoringResponse.Profit profit = profitIndicator.indicate(series, record);
        BacktestResDto.QuantScoringResponse.Effect effect = effectIndicator.indicate(record, profit, stable);
        BacktestResDto.QuantScoringResponse.Growth growth = growthIndicator.indicate(series, record);

        // 최종 점수 계산 로직
        double stableScore = scoreCalculator.calculateStableScore(stable);
        double profitScore = scoreCalculator.calculateProfitScore(profit);
        double effectScore = scoreCalculator.calculateEffectScore(effect);
        double growthScore = scoreCalculator.calculateGrowthScore(growth);

        log.info("안정성 점수 = {}", stableScore);
        log.info("수익성 점수 = {}", profitScore);
        log.info("효율성 점수 = {}", effectScore);
        log.info("성장성 점수 = {}", growthScore);

        double finalScore = scoreCalculator.calculateTotalScore(investType, stableScore, profitScore, effectScore, growthScore);
        log.info("최종 적합도 점수 = {}", finalScore);

        return BacktestResDto.QuantScoringResponse.builder()
                .stockCode(stockCode)
                .investType(investType)
                .finalScore(finalScore)
                .stable(stable)
                .growth(growth)
                .effect(effect)
                .profit(profit)
                .build();
    }
}
