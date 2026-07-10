package juby.invest.domain.backtest.dto;

import lombok.Builder;

import java.math.BigDecimal;

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
            int investType, // 투자 유형
            double finalScore, // 최종 적합도
            Stable stable, // 안정성
            Profit profit, // 수익성
            Effect effect, // 효율성
            Growth growth // 성장성
    ){
        @Builder
        public record Stable(
                BigDecimal mdd,
                BigDecimal volatility,
                BigDecimal dVolatility
        ){}
        @Builder
        public record Profit(
                BigDecimal totalReturn,
                BigDecimal annualReturn,
                BigDecimal avgTradeReturn
        ){}
        @Builder
        public record Effect(
                BigDecimal sharpeRatio, // 전체 변동성 대비 수익률
                BigDecimal sortinoRatio, // 하방위험 대비 수익률
                BigDecimal calmarRatio // MDD 대비 수익률
        ){}
        @Builder
        public record Growth(
                BigDecimal momentumRatio, // 모멘텀 수익률
                BigDecimal volGrowthRatio, // 거래량 증가율
                int positionCount // 거래횟수
        ){}
    }
}
