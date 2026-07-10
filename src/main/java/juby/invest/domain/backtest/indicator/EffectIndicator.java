package juby.invest.domain.backtest.indicator;

import juby.invest.domain.backtest.dto.BacktestResDto;
import org.springframework.stereotype.Component;
import org.ta4j.core.TradingRecord;

import java.math.BigDecimal;

@Component
public class EffectIndicator {

    /***
     * 효율성 축
     * 데이터의 100% 정합성을 위해 Profit과 Stable DTO의 데이터를 직접 연산합니다.
     */
    public BacktestResDto.QuantScoringResponse.Effect indicate(
            TradingRecord record,
            BacktestResDto.QuantScoringResponse.Profit profit,
            BacktestResDto.QuantScoringResponse.Stable stable) {

        // 1. 거래가 없으면 모두 0
        if (record.getPositions().isEmpty()) {
            return BacktestResDto.QuantScoringResponse.Effect.builder()
                    .sharpeRatio(BigDecimal.ZERO)
                    .sortinoRatio(BigDecimal.ZERO)
                    .calmarRatio(BigDecimal.ZERO)
                    .build();
        }

        // 2. 이미 검증이 완료된 정확한 값들 로드
        double annReturn = profit.annualReturn().doubleValue();
        double volatility = stable.volatility().doubleValue();
        double dVolatility = stable.dVolatility().doubleValue();
        double mdd = stable.mdd().doubleValue();

        // 3. 표준 공식 적용
        double sharpeRatio = (volatility > 0) ? (annReturn / volatility) : 0.0;
        double sortinoRatio = (dVolatility > 0) ? (annReturn / dVolatility) : 0.0;
        double calmarRatio = (mdd > 0) ? (annReturn / mdd) : 0.0;

        // 4. 결과 반환
        return BacktestResDto.QuantScoringResponse.Effect.builder()
                .sharpeRatio(BigDecimal.valueOf(sharpeRatio))
                .sortinoRatio(BigDecimal.valueOf(sortinoRatio))
                .calmarRatio(BigDecimal.valueOf(calmarRatio))
                .build();
    }
}