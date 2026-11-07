package juby.invest.domain.livechat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LiveChatRateLimiter")
class LiveChatRateLimiterTest {

    private static class MovableClock extends Clock {
        private long millis = 1_000_000;
        void advanceMillis(long amount) { millis += amount; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
    }

    private final MovableClock clock = new MovableClock();
    private final LiveChatRateLimiter limiter = new LiveChatRateLimiter(clock);

    @Test
    @DisplayName("1초 안에 다시 보내면 거부하고, 1초가 지나면 허용한다")
    void rejectsWithinInterval() {
        assertThat(limiter.tryAcquire(1L)).isTrue();

        clock.advanceMillis(999);
        assertThat(limiter.tryAcquire(1L)).isFalse();

        clock.advanceMillis(1);
        assertThat(limiter.tryAcquire(1L)).isTrue();
    }

    @Test
    @DisplayName("거부된 시도는 기록되지 않아, 연타해도 대기 시간이 늘어나지 않는다")
    void rejectedAttemptDoesNotExtendWait() {
        assertThat(limiter.tryAcquire(1L)).isTrue();

        clock.advanceMillis(500);
        assertThat(limiter.tryAcquire(1L)).isFalse();

        clock.advanceMillis(500);
        assertThat(limiter.tryAcquire(1L)).isTrue();
    }

    @Test
    @DisplayName("회원마다 따로 센다")
    void perMember() {
        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(2L)).isTrue();
    }
}
