package juby.invest.domain.backtest.indicator;

import juby.invest.domain.backtest.dto.BacktestResDto;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

@Component
public class StableIndicator {

    /**
     * 안정성 축
     * MDD, 변동성, 하방변동성
     */
    public BacktestResDto.QuantScoringResponse.Stable indicate(BarSeries series, TradingRecord record){

        CashFlow cashFlow = new CashFlow(series, record);
        ROCIndicator roc = new ROCIndicator(cashFlow, 1);

        int barCount = series.getBarCount();

        // 데이터가 부족할 경우 0 반환 방어 로직
        if (barCount < 2) {
            return BacktestResDto.QuantScoringResponse.Stable.builder()
                    .mdd(series.numFactory().numOf(0).bigDecimalValue())
                    .volatility(series.numFactory().numOf(0).bigDecimalValue())
                    .dVolatility(series.numFactory().numOf(0).bigDecimalValue())
                    .build();
        }

        StandardDeviationIndicator volatilityIndicator = new StandardDeviationIndicator(roc, barCount);

        // 1. 일일 변동성 (ta4j의 ROC는 기본적으로 % 값을 반환하므로 3.05는 305%가 아닌 3.05%를 의미함)
        Num dailyVol = volatilityIndicator.getValue(series.getEndIndex());
        Num dailyDownVol = calculateDownVolatility(series, roc);

        // 2. 연환산 및 소수점 정규화 (Annualization & Decimal Normalization)
        // % 단위를 소수점(0.0305)으로 맞추고 루트 252를 곱해 연환산 적용
        double annualizedFactor = Math.sqrt(252) / 100.0;
        Num annVolatility = dailyVol.multipliedBy(series.numFactory().numOf(annualizedFactor));
        Num annDownVolatility = dailyDownVol.multipliedBy(series.numFactory().numOf(annualizedFactor));

        return BacktestResDto.QuantScoringResponse.Stable.builder()
                .mdd(new MaximumDrawdownCriterion().calculate(series, record).bigDecimalValue())
                .volatility(annVolatility.bigDecimalValue())
                .dVolatility(annDownVolatility.bigDecimalValue())
                .build();
    }

    private Num calculateDownVolatility(BarSeries series, ROCIndicator rocIndicator){

        Num sumOfSquaredDownside = series.numFactory().numOf(0);
        Num targetReturn = series.numFactory().numOf(0);
        int count = series.getBarCount();

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++){
            Num currentReturn = rocIndicator.getValue(i);

            if (currentReturn.isLessThan(targetReturn)){
                Num deviation = currentReturn.minus(targetReturn);
                sumOfSquaredDownside = sumOfSquaredDownside.plus(deviation.multipliedBy(deviation));
            }
        }

        return sumOfSquaredDownside.dividedBy(series.numFactory().numOf(count)).sqrt();
    }
}
