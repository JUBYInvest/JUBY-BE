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

    public BacktestResDto.QuantScoringResponse.Stable indicate(BarSeries series, TradingRecord record){

        CashFlow cashFlow = new CashFlow(series, record); // 계좌 잔고의 시계열 데이터
        ROCIndicator roc = new ROCIndicator(cashFlow, 1); // Rate Of Change

        int barCount = series.getBarCount();
        StandardDeviationIndicator volatility = new StandardDeviationIndicator(roc, barCount-1); // 평균 대비 수익률 표준편차

        // 하방 변동성 계산
        Num dVolatility = calculateDownVolatility(series, roc);

        return BacktestResDto.QuantScoringResponse.Stable.builder()
                .mdd(new MaximumDrawdownCriterion().calculate(series, record).bigDecimalValue()) // 최대낙폭
                .volatility(volatility.getValue(series.getEndIndex()).bigDecimalValue()) // 변동성
                .dVolatility(dVolatility.bigDecimalValue()) // 하방변동성
                .build();
    }

    /***
     * 하방 변동성 계산 로직
     *
     */
    private Num calculateDownVolatility(BarSeries series, ROCIndicator rocIndicator){

        Num sumOfSquaredDownside = series.numFactory().numOf(0);
        Num targetReturn = series.numFactory().numOf(0);
        int count = series.getBarCount();

        // 손실이 난 구간만 계산
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++){

            // 당일 수익률
            Num currentReturn = rocIndicator.getValue(i);

            // 당일 수익률이 0보다 작을 때만. 즉, 손실 발생 시
            if (currentReturn.isLessThan(targetReturn)){
                Num deviation = currentReturn.minus(targetReturn).pow(2);
                sumOfSquaredDownside = sumOfSquaredDownside.plus(deviation);
            }
        }

        Num downsideVariance = sumOfSquaredDownside.dividedBy(series.numFactory().numOf(count-1));

        return downsideVariance.sqrt();
    }
}
