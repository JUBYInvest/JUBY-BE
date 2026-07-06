package juby.invest.domain.backtest.converter;

import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.exception.code.BacktestErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ScoreCalculator {

    /***
     * 안정성 축 정규화 (각 지표를 합산하여 100점 만점에 몇점 식으로)
     */
    public double calculateStableScore(BacktestResDto.QuantScoringResponse.Stable stable){

        // mdd 정규화
        double mddScore = normalizeNegative(stable.mdd().doubleValue(), 0.05, 0.4);

        // 변동성 정규화
        double volatility = normalizeNegative(stable.volatility().doubleValue(), 0.05, 0.4);

        // 하방변동성 정규화
        double dVolatility = normalizeNegative(stable.dVolatility().doubleValue(), 0.03, 0.3);

        // 최종 점수 리턴
        double score = mddScore*0.5 + volatility*0.25 + dVolatility*0.25;

        return Math.round(score*100) / 100.0;
    }

    public double calculateProfitScore(BacktestResDto.QuantScoringResponse.Profit profit) {

        // 총 수익률 정규화
        double totalReturn = normalizePositive(profit.totalReturn().doubleValue(), 0.0, 0.1);
//        log.info("totalReturn: {}", totalReturn);
        // 연평균 수익률 정규화
        double annualReturn = normalizePositive(profit.annualReturn().doubleValue(), 0.0, 0.5);
//        log.info("annualReturn: {}", annualReturn);
        // 평균 거래 수익률 정규화
        double avgTradeReturn = normalizePositive(profit.avgTradeReturn().doubleValue(), 0.0, 0.1);
//        log.info("avgTradeReturn: {}", avgTradeReturn);
        // 최종 점수 리턴
        double score = totalReturn*0.1 + annualReturn*0.6 + avgTradeReturn*0.3;
//        log.info("totalScore: {}", score);
        return Math.round(score*100)/100.0;
    }

    public double calculateEffectScore(BacktestResDto.QuantScoringResponse.Effect effect) {

        // 샤프 지수 정규화
        double sharpeRatio = normalizePositive(effect.sharpeRatio().doubleValue(), 0.0, 2.0);

        // 소르티노 지수 정규화
        double sortinoRatio = normalizePositive(effect.sortinoRatio().doubleValue(), 0.0, 3.0);

        // 칼마 지수 정규화
        double calmarRatio = normalizePositive(effect.calmarRatio().doubleValue(), 0.0, 3.0);

        // 최종 효율성 점수 반환
        double score = sharpeRatio*0.5 + sortinoRatio*0.25 + calmarRatio*0.25;
        return Math.round(score*100)/100.0;
    }

    public double calculateGrowthScore(BacktestResDto.QuantScoringResponse.Growth growth) {

        // 모멘텀 성익률 정규화
        double momentumRaio = normalizePositive(growth.momentumRatio().doubleValue(), -0.20, 0.20);
        // 거래량 증가율 정규화
        double volGrowthRatio = normalizePositive(growth.volGrowthRatio().doubleValue(), 0.0, 0.5);
        // 거래횟수 정규화
        double positionCount = normalizePositive((double) growth.positionCount(), 0.0, 20);
        // 최종 성장성 점수 반환
        double score = momentumRaio*0.4 + volGrowthRatio*0.3 + positionCount*0.3;
        return Math.round(score*100)/100.0;
    }

    public double calculateTotalScore(int investType, double stableScore, double profitScore, double effectScore, double growthScore) {

        double totalScore = 0;

        Double[] score = new Double[]{stableScore, profitScore, effectScore, growthScore};
        Double[] weightByType = switch (investType) {
            case 1 -> new Double[]{0.4, 0.15, 0.3, 0.15};
            case 2 -> new Double[]{0.35, 0.2, 0.3, 0.15};
            case 3 -> new Double[]{0.25, 0.25, 0.25, 0.25};
            case 4 -> new Double[]{0.15, 0.3, 0.25, 0.3};
            case 5 -> new Double[]{0.1, 0.3, 0.2, 0.4};
            default -> throw new ProjectException(BacktestErrorCode.INVEST_TYPE_NOT_FOUND);
        };

        for (int i = 0; i < 4; i++){
            totalScore += score[i]*weightByType[i];
        }
        return totalScore;
    }

    private double normalizePositive(double rawValue, double minValue, double maxValue){
        if (maxValue == minValue) return 0.0;

        double score = ((rawValue - minValue) / (maxValue - minValue)) * 100;

        return Math.clamp(score, 0.0, 100.0);
    }

    private double normalizeNegative(double rawValue, double bestValue, double worstValue){
        if (worstValue == bestValue) return 0;

        double score = ((worstValue - rawValue) / (worstValue - bestValue)) * 100;

        return Math.clamp(score, 0.0, 100.0);
    }
}
