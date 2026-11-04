package juby.invest.domain.kis.realtime.service;

import juby.invest.domain.kis.realtime.dto.RealtimeIndexTick;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("실시간 지수 저장소")
class RealtimeIndexStoreTest {

    /** 신선도 경계를 실제 대기 없이 검증하기 위한, 수동으로 흐르는 시계 */
    private static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-03T04:00:00Z"); // 13:00 KST

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }

        void advanceMinutes(long minutes) { now = now.plusSeconds(minutes * 60); }
    }

    private final MovableClock clock = new MovableClock();
    private final RealtimeIndexStore store = new RealtimeIndexStore(clock);

    private static RealtimeIndexTick tick(String indexCode, String currentIndex) {
        return new RealtimeIndexTick(indexCode, "133000",
                new BigDecimal(currentIndex), new BigDecimal("-0.37"),
                new BigDecimal("2550.10"), new BigDecimal("2561.44"), new BigDecimal("2538.07"),
                new BigDecimal("1234"), new BigDecimal("5678"));
    }

    @Test
    @DisplayName("업종코드를 키로 저장한다")
    void storesByIndexCode() {
        store.put(tick("0001", "2543.21"));

        assertThat(store.findFresh("0001").orElseThrow().tick().currentIndex())
                .isEqualByComparingTo("2543.21");
        assertThat(store.findFresh("1001")).isEmpty();
    }

    @Test
    @DisplayName("같은 지수의 값이 들어오면 최신값으로 덮어쓴다")
    void overwritesWithLatestValue() {
        store.put(tick("0001", "2543.21"));
        clock.advanceMinutes(1);
        store.put(tick("0001", "2551.00"));

        assertThat(store.findFresh("0001").orElseThrow().tick().currentIndex())
                .isEqualByComparingTo("2551.00");
        assertThat(store.findAllFresh()).hasSize(1);
    }

    @Test
    @DisplayName("신선도 기준을 넘긴 값은 없는 것으로 본다")
    void dropsStaleValue() {
        store.put(tick("0001", "2543.21"));

        clock.advanceMinutes(RealtimeSnapshotStore.STALE_AFTER.toMinutes() + 1);

        assertThat(store.findFresh("0001")).isEmpty();
    }
}
