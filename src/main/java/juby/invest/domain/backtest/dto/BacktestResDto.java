package juby.invest.domain.backtest.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public class BacktestResDto {

    @Builder
    public record GetInfo(
            String stockCode, // 종목 코드
            String strategyName, // 전략 이름
            int positionCount, // 체결 포지션 수
            BigDecimal totalReturn, // 누적 수익률
            BigDecimal annualizedReturn, // 연평균 수익률
            BigDecimal sharpeRatio, // 샤프 비율
            BigDecimal stdDeviation, // 수익률 표준편차
            BigDecimal maxDrawdown // MDD
    ){}

    @Builder
    public record QuantScoringResponse(
            String stockCode, // 종목 코드
            int investType, //
            Stable stable,
            Profit profit,
            Effect effect,
            Growth growth
    ){
        public record Stable(
                BigDecimal mdd,
                BigDecimal volatility,
                BigDecimal dVolatility
        ){}

        public record Profit(
                BigDecimal totalReturn,
                BigDecimal annualReturn,
                BigDecimal avgTradeReturn
        ){}

        public record Effect(
                BigDecimal sharpeRatio, // 전체 변동성 대비 수익률
                BigDecimal sortinoRatio, // 하방위험 대비 수익률
                BigDecimal calmarRatio // MDD 대비 수익률
        ){}

        public record Growth(
                BigDecimal momentRatio, // 모멘텀 수익률
                BigDecimal volGrowthRatio, // 거래량 증가율
                int positionCount // 거래횟수
        ){}
    }
}
