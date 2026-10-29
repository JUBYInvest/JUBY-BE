package juby.invest.domain.backtest.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BreakoutStrategy 돌파 전략")
class BreakoutStrategyTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BreakoutStrategy sut = new BreakoutStrategy();

    private static void addBar(BarSeries series, LocalDate date, int close, long volume) {
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(date.atTime(15, 30).atZone(KST).toInstant())
                .openPrice(close).highPrice(close).lowPrice(close).closePrice(close)
                .volume(volume)
                .build());
    }

    @Test
    @DisplayName("20일 최고가를 상향 돌파하고 거래량이 평균의 1.5배 이상이면 매수 신호를 낸다")
    void entersOnBreakoutWithVolumeSurge() {
        BarSeries series = new BaseBarSeriesBuilder().withName("test").build();
        LocalDate date = LocalDate.of(2025, 1, 2);

        // 30봉 동안 50,000원 박스권 + 평균 거래량 1,000,000주
        for (int i = 0; i < 30; i++) {
            addBar(series, date, 50_000 + (i % 2 == 0 ? 100 : -100), 1_000_000);
            date = date.plusDays(1);
        }

        // 돌파 봉: 종가가 박스 상단을 넘고 거래량도 평균의 2배
        addBar(series, date, 53_000, 2_000_000);

        Strategy strategy = sut.strategy(series);
        TradingRecord record = new BaseTradingRecord();

        assertThat(strategy.getEntryRule().isSatisfied(series.getEndIndex(), record)).isTrue();
    }

    @Test
    @DisplayName("20일 최고가를 돌파해도 거래량이 평균의 1.5배 미만이면 매수 신호를 내지 않는다")
    void doesNotEnterWithoutVolumeSurge() {
        BarSeries series = new BaseBarSeriesBuilder().withName("test").build();
        LocalDate date = LocalDate.of(2025, 1, 2);

        for (int i = 0; i < 30; i++) {
            addBar(series, date, 50_000 + (i % 2 == 0 ? 100 : -100), 1_000_000);
            date = date.plusDays(1);
        }

        // 돌파는 했지만 거래량이 평균 수준
        addBar(series, date, 53_000, 1_000_000);

        Strategy strategy = sut.strategy(series);
        TradingRecord record = new BaseTradingRecord();

        assertThat(strategy.getEntryRule().isSatisfied(series.getEndIndex(), record)).isFalse();
    }

    /***
     * 50,000원 박스권 30봉 -> 53,000원 돌파 1봉 -> 이후 전달받은 종가들.
     * 돌파 봉(index 30) 이후 20일 최고가(전일까지)는 53,000원으로 고정된다.
     */
    private static BarSeries breakoutThen(int... closesAfterBreakout) {
        BarSeries series = new BaseBarSeriesBuilder().withName("test").build();
        LocalDate date = LocalDate.of(2025, 1, 2);

        for (int i = 0; i < 30; i++) {
            addBar(series, date, 50_000 + (i % 2 == 0 ? 100 : -100), 1_000_000);
            date = date.plusDays(1);
        }

        addBar(series, date, 53_000, 2_000_000);
        date = date.plusDays(1);

        for (int close : closesAfterBreakout) {
            addBar(series, date, close, 1_000_000);
            date = date.plusDays(1);
        }

        return series;
    }

    /** index 30에서 53,000원에 매수한 상태의 매매 기록. */
    private static TradingRecord openedAtBreakout(BarSeries series) {
        TradingRecord record = new BaseTradingRecord();
        record.enter(30, series.numFactory().numOf(53_000), series.numFactory().one());
        return record;
    }

    @Test
    @DisplayName("ATR 손절에 닿지 않아도 돌파 기준선을 하향 이탈하면 매도 신호를 낸다")
    void exitsOnBreakoutLineBreachAlone() {
        // 53,000 -> 52,800. 기준선(53,000) 하향 이탈이지만 손절가(53,000 - 2*ATR)에는 못 미친다.
        BarSeries series = breakoutThen(52_800);
        Strategy strategy = sut.strategy(series);
        TradingRecord record = openedAtBreakout(series);

        int index = series.getEndIndex();

        assertThat(strategy.getExitRule().isSatisfied(index, record))
                .as("돌파선 이탈만으로도 매도되어야 한다").isTrue();
    }

    @Test
    @DisplayName("돌파 기준선 이탈 교차가 없어도 ATR 손절가에 닿으면 매도 신호를 낸다")
    void exitsOnAtrStopAlone() {
        // 52,800에서 이미 기준선 아래로 내려온 뒤라 다음 봉에는 '하향 교차'가 없다.
        // 50,000은 손절가(53,000 - 2*ATR) 아래다.
        BarSeries series = breakoutThen(52_800, 50_000);
        Strategy strategy = sut.strategy(series);
        TradingRecord record = openedAtBreakout(series);

        int index = series.getEndIndex();

        assertThat(strategy.getExitRule().isSatisfied(index, record))
                .as("ATR 손절만으로도 매도되어야 한다").isTrue();
    }

    @Test
    @DisplayName("기준선 위에 있고 손절가에도 닿지 않으면 매도 신호를 내지 않는다")
    void holdsWhenNeitherExitConditionMet() {
        // 53,500은 기준선(53,000) 위이고 손절가와도 거리가 멀다.
        BarSeries series = breakoutThen(53_500);
        Strategy strategy = sut.strategy(series);
        TradingRecord record = openedAtBreakout(series);

        assertThat(strategy.getExitRule().isSatisfied(series.getEndIndex(), record)).isFalse();
    }

    /***
     * 윈도우형 성분(HighestValue 20 + PreviousValue 1 + 교차 판정)은 21봉이면 오차가 0이고,
     * 재귀형 ATR(14)은 실측 요구치가 최대 58봉이었다.
     * 100봉은 그 실측 최대를 덮으면서 1년 프리셋 가용치(197봉) 아래에 충분히 머문다.
     */
    @Test
    @DisplayName("워밍업 봉 수는 100봉이다")
    void warmupCoversAtrTail() {
        assertThat(sut.warmupBars()).isEqualTo(100);
        assertThat(sut.warmupBars()).isLessThan(197);
    }
}
