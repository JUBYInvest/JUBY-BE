package juby.invest.domain.backtest.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public class BacktestResDTO {

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
}
