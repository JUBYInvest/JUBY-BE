package juby.invest.domain.backtest.converter;

import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.indicator.*;
import juby.invest.domain.stock.entity.Stock;
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

    /***
     * 함수 기능: 전략 실행 후 매매 기록을 바탕으로, 4가지 축(안정석, 수익성, 효율성, 성장성)에 맞게
     *           점수를 수치화한다. 그리고 4가지 점수를 유형에 맞게 가중치를 두어 최종 적합도 점수를 반환한다.
     * @param stock 종목
     * @param investType 투자 성향
     * @param series 워밍업 + 전략 실행 구간 일봉 데이터 지표
     * @param record 전략에 따른 매매 기록
     * @param evalStartIndex 전략 실행 시작 인덱스
     * @return QuantScoringResponse 최종 적합도
     */
    public BacktestResDto.QuantScoringResponse converter(
            Stock stock, int investType, BarSeries series, TradingRecord record, int evalStartIndex){

        BacktestResDto.QuantScoringResponse.Stable stable = stableIndicator.indicate(series, record, evalStartIndex);
        BacktestResDto.QuantScoringResponse.Profit profit = profitIndicator.indicate(series, record, evalStartIndex);
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
                .stockCode(stock.getStockCode())
                .investType(investType)
                .finalScore(finalScore)
                .stable(stable)
                .growth(growth)
                .effect(effect)
                .profit(profit)
                .build();
    }
}
