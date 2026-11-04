package juby.invest.domain.kis.realtime.service;

import juby.invest.domain.kis.realtime.dto.RealtimePrice;
import juby.invest.domain.kis.realtime.dto.RealtimeTick;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("실시간 시세 저장소")
class RealtimePriceStoreTest {

    /** 신선도 경계를 실제 대기 없이 검증하기 위한, 수동으로 흐르는 시계 */
    private static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-03T04:00:00Z"); // 13:00 KST

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }

        void advanceMinutes(long minutes) { now = now.plusSeconds(minutes * 60); }
    }

    private final MovableClock clock = new MovableClock();
    private final RealtimePriceStore store = new RealtimePriceStore(clock);

    private static RealtimeTick tick(String stockCode, int currentPrice) {
        return new RealtimeTick(stockCode, "130000", currentPrice, 1.5,
                1000, 1100, 900, 10_000L, 20_000L);
    }

    @Test
    @DisplayName("저장한 종목의 시세를 수신 시각과 함께 반환한다")
    void storesAndFindsTick() {
        store.put(tick("005930", 73100));

        RealtimePrice price = store.findFresh("005930").orElseThrow();
        assertThat(price.tick().currentPrice()).isEqualTo(73100);
        assertThat(price.receivedAt()).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("저장된 적 없는 종목은 비어 있다")
    void returnsEmptyForUnknownStock() {
        assertThat(store.findFresh("000000")).isEmpty();
    }

    @Test
    @DisplayName("같은 종목의 체결이 들어오면 최신값으로 덮어쓴다")
    void overwritesWithLatestTick() {
        store.put(tick("005930", 73100));
        clock.advanceMinutes(1);
        store.put(tick("005930", 73500));

        assertThat(store.findFresh("005930").orElseThrow().tick().currentPrice()).isEqualTo(73500);
        assertThat(store.findAllFresh()).hasSize(1);
    }

    @Test
    @DisplayName("신선도 기준을 정확히 채운 값은 아직 유효하다")
    void keepsValueAtStalenessBoundary() {
        store.put(tick("005930", 73100));

        clock.advanceMinutes(RealtimeSnapshotStore.STALE_AFTER.toMinutes());

        assertThat(store.findFresh("005930")).isPresent();
    }

    @Test
    @DisplayName("신선도 기준을 넘긴 값은 없는 것으로 본다")
    void dropsStaleValue() {
        store.put(tick("005930", 73100));

        clock.advanceMinutes(RealtimeSnapshotStore.STALE_AFTER.toMinutes() + 1);

        assertThat(store.findFresh("005930")).isEmpty();
    }

    @Test
    @DisplayName("전체 조회에서도 오래된 값은 제외한다")
    void excludesStaleValuesFromSnapshot() {
        store.put(tick("005930", 73100));

        clock.advanceMinutes(RealtimeSnapshotStore.STALE_AFTER.toMinutes() + 1);
        store.put(tick("035720", 41500));

        assertThat(store.findAllFresh()).containsOnlyKeys("035720");
    }
}
