package juby.invest.domain.backtest.strategy;

import juby.invest.domain.backtest.indicator.ta4j.WilderRsiIndicator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RsiReversionStrategy RSI 역추세 전략")
class RsiReversionStrategyTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RsiReversionStrategy sut = new RsiReversionStrategy();

    /***
     * 완만한 등락 -> 급락(RSI 40 하향) -> 급등(RSI 70 상향) 순서로 움직이는 종가를 만든다.
     */
    private static BarSeries series() {
        BarSeries series = new BaseBarSeriesBuilder().withName("test").build();
        LocalDate date = LocalDate.of(2025, 1, 2);

        int price = 50000;

        for (int i = 0; i < 60; i++) {
            if (i < 20) {
                // 시드 구간: 소폭 등락
                price += (i % 2 == 0) ? 300 : -250;
            } else if (i < 35) {
                // 급락 구간
                price -= 900;
            } else {
                // 급등 구간
                price += 1200;
            }

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
    @DisplayName("RSI가 40 아래로 내려가면 매수 신호를 낸다")
    void entersWhenRsiCrossesBelowForty() {
        BarSeries series = series();
        Strategy strategy = sut.strategy(series);
        WilderRsiIndicator rsi = new WilderRsiIndicator(new ClosePriceIndicator(series), 14);
        TradingRecord record = new BaseTradingRecord();

        int crossedBelow = -1;

        for (int i = 15; i <= series.getEndIndex(); i++) {
            double previous = rsi.getValue(i - 1).doubleValue();
            double current = rsi.getValue(i).doubleValue();

            if (previous >= 40 && current < 40) {
                crossedBelow = i;
                break;
            }
        }

        assertThat(crossedBelow).as("RSI가 40을 하향 돌파하는 봉이 있어야 한다").isGreaterThan(0);
        assertThat(strategy.getEntryRule().isSatisfied(crossedBelow, record)).isTrue();
        assertThat(strategy.getExitRule().isSatisfied(crossedBelow, record)).isFalse();
    }

    @Test
    @DisplayName("RSI가 70 위로 올라서면 매도 신호를 낸다")
    void exitsWhenRsiCrossesAboveSeventy() {
        BarSeries series = series();
        Strategy strategy = sut.strategy(series);
        WilderRsiIndicator rsi = new WilderRsiIndicator(new ClosePriceIndicator(series), 14);
        TradingRecord record = new BaseTradingRecord();

        int crossedAbove = -1;

        for (int i = 15; i <= series.getEndIndex(); i++) {
            double previous = rsi.getValue(i - 1).doubleValue();
            double current = rsi.getValue(i).doubleValue();

            if (previous <= 70 && current > 70) {
                crossedAbove = i;
                break;
            }
        }

        assertThat(crossedAbove).as("RSI가 70을 상향 돌파하는 봉이 있어야 한다").isGreaterThan(0);
        assertThat(strategy.getExitRule().isSatisfied(crossedAbove, record)).isTrue();
        assertThat(strategy.getEntryRule().isSatisfied(crossedAbove, record)).isFalse();
    }

    @Test
    @DisplayName("워밍업 봉 수는 RSI 수렴에 필요한 150봉이다")
    void requiresConvergenceWarmup() {
        assertThat(sut.warmupBars()).isEqualTo(150);
    }
}
