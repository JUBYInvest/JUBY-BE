package juby.invest.domain.backtest.indicator.ta4j;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WilderRsiIndicator 와일더 시드 RSI")
class WilderRsiIndicatorTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /***
     * 삼성전자 2025-01-02 ~ 2025-01-23 실제 종가.
     * index 0 이 2025-01-02 이므로 변화량 14개는 index 1~14 (2025-01-03 ~ 2025-01-22) 이고
     * 첫 RSI는 index 14에서 나온다.
     */
    private static final int[] CLOSES = {
            53400, 54400, 55900, 55400, 57300, 56100, 55300, 54100,
            53900, 53700, 54300, 53700, 53400, 53500, 54300, 53700
    };

    private static BarSeries series(int... closes) {
        BarSeries series = new BaseBarSeriesBuilder().withName("test").build();
        LocalDate date = LocalDate.of(2025, 1, 2);

        for (int close : closes) {
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(date.atTime(15, 30).atZone(KST).toInstant())
                    .openPrice(close).highPrice(close).lowPrice(close).closePrice(close)
                    .volume(1)
                    .build());
            date = date.plusDays(1);
        }

        return series;
    }

    private static WilderRsiIndicator rsi(BarSeries series) {
        return new WilderRsiIndicator(new ClosePriceIndicator(series), 14);
    }

    @Test
    @DisplayName("변화량 14개가 모이기 전에는 값이 없다")
    void returnsNaNBeforeSeed() {
        WilderRsiIndicator rsi = rsi(series(CLOSES));

        for (int index = 0; index <= 13; index++) {
            assertThat(rsi.getValue(index).isNaN())
                    .as("index %d", index)
                    .isTrue();
        }

        assertThat(rsi.getValue(14).isNaN()).isFalse();
        assertThat(rsi.getCountOfUnstableBars()).isEqualTo(14);
    }

    @Test
    @DisplayName("첫 값은 변화량 14개의 단순 평균으로 시드를 잡는다")
    void seedsWithSimpleAverageOfFirstChanges() {
        // 상승분 합 = 1000 + 1500 + 1900 + 600 + 100 + 800 = 5900  -> 평균 5900/14
        // 하락분 합 =  500 + 1200 +  800 + 1200 + 200 + 200 + 600 + 300 = 5000  -> 평균 5000/14
        // RS  = 5900 / 5000 = 1.18
        // RSI = 100 - 100 / (1 + 1.18) = 54.1284404...
        double seed = rsi(series(CLOSES)).getValue(14).doubleValue();

        assertThat(seed).isCloseTo(54.1284404, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("시드 이후에는 와일더 평활화로 이어서 계산한다")
    void continuesWithWilderSmoothing() {
        // index 15 종가 53700 -> 변화량 -600 (상승 0, 하락 600)
        // 평균 상승 = (5900/14 * 13 + 0)   / 14 = 391.3265306...
        // 평균 하락 = (5000/14 * 13 + 600) / 14 = 374.4897959...
        // RS  = 391.3265306 / 374.4897959 = 1.0449591...
        // RSI = 100 - 100 / (1 + 1.0449591) = 51.0992672...
        double next = rsi(series(CLOSES)).getValue(15).doubleValue();

        assertThat(next).isCloseTo(51.0992672, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("종가가 전혀 움직이지 않으면 과매도가 아니라 중립값 50을 반환한다")
    void returnsNeutralWhenFlat() {
        int[] flat = new int[20];
        java.util.Arrays.fill(flat, 50000);

        // ta4j의 RSIIndicator는 이 경우 0을 돌려주는데, 그러면 보합 구간이 과매도로 오인된다.
        assertThat(rsi(series(flat)).getValue(19).doubleValue()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("하락이 없으면 100을 반환한다")
    void returnsHundredWithoutLoss() {
        int[] rising = new int[20];

        for (int i = 0; i < rising.length; i++) {
            rising[i] = 50000 + (i * 500);
        }

        assertThat(rsi(series(rising)).getValue(19).doubleValue()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("ta4j 기본 RSIIndicator와 달리 첫 값이 시장과 무관한 극단값으로 튀지 않는다")
    void doesNotStartAtExtremeUnlikeTa4jDefault() {
        BarSeries series = series(CLOSES);

        double wilder = rsi(series).getValue(14).doubleValue();
        double ta4j = new org.ta4j.core.indicators.RSIIndicator(
                new ClosePriceIndicator(series), 14).getValue(14).doubleValue();

        // ta4j는 한 봉만으로 시드를 잡아 100(또는 0)에서 출발한다.
        assertThat(ta4j).isEqualTo(100.0);
        assertThat(wilder).isBetween(40.0, 70.0);
    }
}
