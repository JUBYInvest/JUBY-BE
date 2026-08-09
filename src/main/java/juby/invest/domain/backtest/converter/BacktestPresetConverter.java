package juby.invest.domain.backtest.converter;

import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.entity.BacktestPresetResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BacktestPresetConverter {

    /***
     * 함수 기능: 프리셋 결과 엔티티를 응답 DTO로 변환한다.
     */
    public BacktestResDto.PresetResponse toResponse(BacktestPresetResult entity) {

        BacktestResDto.QuantScoringResponse result = BacktestResDto.QuantScoringResponse.builder()
                .stockCode(entity.getStock().getStockCode())
                .investType(entity.getInvestType())
                .finalScore(entity.getFinalScore())
                .stable(BacktestResDto.QuantScoringResponse.Stable.builder()
                        .mdd(nullSafe(entity.getMdd()))
                        .volatility(nullSafe(entity.getVolatility()))
                        .dVolatility(nullSafe(entity.getDVolatility()))
                        .build())
                .profit(BacktestResDto.QuantScoringResponse.Profit.builder()
                        .totalReturn(nullSafe(entity.getTotalReturn()))
                        .annualReturn(nullSafe(entity.getAnnualReturn()))
                        .avgTradeReturn(nullSafe(entity.getAvgTradeReturn()))
                        .build())
                .effect(BacktestResDto.QuantScoringResponse.Effect.builder()
                        .sharpeRatio(nullSafe(entity.getSharpeRatio()))
                        .sortinoRatio(nullSafe(entity.getSortinoRatio()))
                        .calmarRatio(nullSafe(entity.getCalmarRatio()))
                        .build())
                .growth(BacktestResDto.QuantScoringResponse.Growth.builder()
                        .momentumRatio(nullSafe(entity.getMomentumRatio()))
                        .volGrowthRatio(nullSafe(entity.getVolGrowthRatio()))
                        .positionCount(entity.getPositionCount())
                        .build())
                .build();

        return BacktestResDto.PresetResponse.builder()
                .stockCode(entity.getStock().getStockCode())
                .investType(entity.getInvestType())
                .period(entity.getPeriod())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .updatedAt(entity.getUpdatedAt())
                .result(result)
                .build();
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
