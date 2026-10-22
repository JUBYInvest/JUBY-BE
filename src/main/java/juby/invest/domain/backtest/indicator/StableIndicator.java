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
     * @param evalStartIndex 평가 대상(사용자 요청) 구간의 첫 봉 index. 그 앞은 지표 계산 전용 워밍업 구간이다.
     */
    public BacktestResDto.QuantScoringResponse.Stable indicate(BarSeries series, TradingRecord record, int evalStartIndex){

        // CashFlow: 계좌 잔고의 시계열 데이터
        // 첫 번째 봉부터 마지막 봉까지 루프를 돌며 매 시점의 자산 가치를 계산하여 배열 형태로 저장한다.
        // 초기 자본은 1을 기준으로 시작한다.
        CashFlow cashFlow = new CashFlow(series, record);

        // ROC (Rate Of Change): CashFlow가 전일 대비 몇 % 변했는지를 나타내는 모멘텀 지표
        ROCIndicator roc = new ROCIndicator(cashFlow, 1);

        // 워밍업 구간은 포지션이 없어 CashFlow가 1.0으로 평탄하다.
        // 그 구간을 포함하면 변동성이 기계적으로 희석되므로 요청 구간의 봉 수만 센다.
        int barCount = series.getEndIndex() - evalStartIndex + 1;

        // 데이터가 부족할 경우 0 반환 방어 로직
        if (barCount < 2) {
            return BacktestResDto.QuantScoringResponse.Stable.builder()
                    .mdd(series.numFactory().numOf(0).bigDecimalValue())
                    .volatility(series.numFactory().numOf(0).bigDecimalValue())
                    .dVolatility(series.numFactory().numOf(0).bigDecimalValue())
                    .build();
        }

        // 표준편차
        StandardDeviationIndicator volatilityIndicator = new StandardDeviationIndicator(roc, barCount);

        // 1. 일일 변동성 (ta4j의 ROC는 기본적으로 % 값을 반환하므로 3.05는 305%가 아닌 3.05%를 의미함)
        Num dailyVol = volatilityIndicator.getValue(series.getEndIndex());
        Num dailyDownVol = calculateDownVolatility(series, roc, evalStartIndex, barCount);

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

    private Num calculateDownVolatility(BarSeries series, ROCIndicator rocIndicator, int evalStartIndex, int count){

        Num sumOfSquaredDownside = series.numFactory().numOf(0);
        Num targetReturn = series.numFactory().numOf(0);

        for (int i = evalStartIndex; i <= series.getEndIndex(); i++){
            Num currentReturn = rocIndicator.getValue(i);

            if (currentReturn.isLessThan(targetReturn)){
                Num deviation = currentReturn.minus(targetReturn);
                sumOfSquaredDownside = sumOfSquaredDownside.plus(deviation.multipliedBy(deviation));
            }
        }

        return sumOfSquaredDownside.dividedBy(series.numFactory().numOf(count)).sqrt();
    }
}
