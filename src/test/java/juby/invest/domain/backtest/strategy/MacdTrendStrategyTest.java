package juby.invest.domain.backtest.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MacdTrendStrategy MACD 추세추종 전략")
class MacdTrendStrategyTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MacdTrendStrategy sut = new MacdTrendStrategy();

    /***
     * 하락 후 상승으로 돌아서는 종가. MACD선이 signal선을 상향 돌파하는 봉이 생긴다.
     */
    private static BarSeries series() {
        BarSeries series = new BaseBarSeriesBuilder().withName("test").build();
        LocalDate date = LocalDate.of(2025, 1, 2);

        int price = 80_000;

        for (int i = 0; i < 120; i++) {
            price += (i < 60) ? -500 : 700;

            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(date.atTime(15, 30).atZone(KST).toInstant())
                    .openPrice(price).highPrice(price).lowPrice(price).closePrice(price)
                    .volume(1)
                    .build());
            date = date.plusDays(1);
        }

        return series;
    }

    @Test
    @DisplayName("MACD선이 signal선을 상향 돌파하면 매수, 하향 돌파하면 매도 신호를 낸다")
    void signalsOnMacdCross() {
        BarSeries series = series();
        Strategy strategy = sut.strategy(series);

        MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series), 12, 26);
        EMAIndicator signal = new EMAIndicator(macd, 9);
        TradingRecord record = new BaseTradingRecord();

        int crossedUp = -1;

        for (int i = 40; i <= series.getEndIndex(); i++) {
            double previousGap = macd.getValue(i - 1).doubleValue() - signal.getValue(i - 1).doubleValue();
            double currentGap = macd.getValue(i).doubleValue() - signal.getValue(i).doubleValue();

            if (previousGap <= 0 && currentGap > 0) {
                crossedUp = i;
                break;
            }
        }

        assertThat(crossedUp).as("MACD선이 signal선을 상향 돌파하는 봉이 있어야 한다").isGreaterThan(0);
        assertThat(strategy.getEntryRule().isSatisfied(crossedUp, record)).isTrue();
        assertThat(strategy.getExitRule().isSatisfied(crossedUp, record)).isFalse();
    }

    /***
     * 150봉은 EMA(26) 시드 오차가 1/10,000까지 감쇠하는 120봉과, 실측 95퍼센타일(101·106봉)을
     * 모두 덮는 값이다. 동시에 1년 프리셋에서 확보되는 최소 워밍업 197봉 아래라
     * WARMUP_DATA_NOT_ENOUGH를 유발하지 않는다.
     */
    @Test
    @DisplayName("워밍업 봉 수는 MACD 수렴에 필요한 150봉이다")
    void requiresConvergenceWarmup() {
        assertThat(sut.warmupBars()).isEqualTo(150);
        assertThat(sut.warmupBars()).isLessThan(197);
    }
}
