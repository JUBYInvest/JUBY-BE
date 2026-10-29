package juby.invest.domain.backtest.indicator.ta4j;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.helpers.GainIndicator;
import org.ta4j.core.indicators.helpers.LossIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/***
 * 와일더(Wilder) 원래 방식으로 시드를 잡는 RSI.
 *
 * ta4j의 {@link org.ta4j.core.indicators.RSIIndicator}는 평균 상승/하락을 MMAIndicator로 구하는데,
 * MMAIndicator는 "단 한 봉의 변화량"을 시드로 쓴다. 그래서 첫 유효값이 시장과 무관한 값으로 출발하고
 * 재귀 평활 때문에 그 오차가 (1 - 1/barCount)^n 으로 아주 천천히 감쇠한다.
 * barCount=14 기준으로 60봉이 지나도 수십 포인트가 틀어져 40/70 임계선 판정을 뒤집는다.
 *
 * 이 클래스는 교과서 정의대로 첫 barCount개 변화량의 '단순 평균'을 시드로 삼아 그 오차를 없앤다.
 * 시드 이후는 동일하게 와일더 평활화로 이어간다.
 */
public class WilderRsiIndicator extends CachedIndicator<Num> {

    private final WilderAverageIndicator averageGain;
    private final WilderAverageIndicator averageLoss;
    private final int barCount;

    public WilderRsiIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.barCount = barCount;
        this.averageGain = new WilderAverageIndicator(new GainIndicator(indicator), barCount);
        this.averageLoss = new WilderAverageIndicator(new LossIndicator(indicator), barCount);
    }

    @Override
    protected Num calculate(int index) {

        Num avgGain = averageGain.getValue(index);
        Num avgLoss = averageLoss.getValue(index);

        // 시드가 아직 만들어지지 않은 구간
        if (Num.isNaNOrNull(avgGain) || Num.isNaNOrNull(avgLoss)) {
            return NaN.NaN;
        }

        NumFactory numFactory = getBarSeries().numFactory();

        if (avgLoss.isZero()) {
            // 하락도 상승도 없었다면 RSI를 정의할 수 없다. 중립값 50을 쓴다.
            // ta4j는 이 경우 0을 반환하는데, 그러면 "RSI가 40 아래로 내려가면 매수" 규칙이
            // 보합 구간을 과매도로 오인해 매수 신호를 내버린다.
            if (avgGain.isZero()) {
                return numFactory.numOf(50);
            }
            return numFactory.hundred();
        }

        Num relativeStrength = avgGain.dividedBy(avgLoss);

        // RSI = 100 - 100 / (1 + RS)
        return numFactory.hundred()
                .minus(numFactory.hundred().dividedBy(numFactory.one().plus(relativeStrength)));
    }

    /***
     * 시드를 만드는 데 barCount개의 변화량이 필요하므로 앞의 barCount봉은 값이 없다.
     * 재귀 지표이므로 이 값은 "NaN이 끝나는 지점"일 뿐 "값이 정확해지는 지점"이 아니다.
     * 전략이 요구할 워밍업 봉 수는 BacktestStrategy#warmupBars 쪽에서 따로 정한다.
     */
    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    /***
     * 와일더 평활 평균.
     * 시드   : 첫 barCount개 변화량의 단순 평균
     * 이후   : (이전 평균 * (barCount - 1) + 당일 변화량) / barCount
     */
    private static final class WilderAverageIndicator extends RecursiveCachedIndicator<Num> {

        private final Indicator<Num> change;
        private final int barCount;

        private WilderAverageIndicator(Indicator<Num> change, int barCount) {
            super(change);
            this.change = change;
            this.barCount = barCount;
        }

        @Override
        protected Num calculate(int index) {

            int beginIndex = getBarSeries().getBeginIndex();

            // 변화량은 beginIndex + 1 부터 생기므로 barCount개를 모으면 시드 index가 된다.
            int seedIndex = beginIndex + barCount;

            if (index < seedIndex) {
                return NaN.NaN;
            }

            NumFactory numFactory = getBarSeries().numFactory();

            if (index == seedIndex) {
                Num sum = numFactory.zero();

                for (int i = beginIndex + 1; i <= seedIndex; i++) {
                    sum = sum.plus(change.getValue(i));
                }

                return sum.dividedBy(numFactory.numOf(barCount));
            }

            Num previousAverage = getValue(index - 1);

            return previousAverage.multipliedBy(numFactory.numOf(barCount - 1))
                    .plus(change.getValue(index))
                    .dividedBy(numFactory.numOf(barCount));
        }

        @Override
        public int getCountOfUnstableBars() {
            return barCount;
        }
    }
}
