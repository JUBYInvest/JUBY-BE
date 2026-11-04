package juby.invest.global.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("로그 쓰로틀")
class LogThrottleTest {

    private static final Duration INTERVAL = Duration.ofSeconds(60);

    /*** 수동으로 흐르는 나노초 시계. 음수에서 시작해 nanoTime의 임의 기준점을 흉내낸다. */
    private final AtomicLong nanos = new AtomicLong(-5_000_000_000L);

    private final LogThrottle throttle = new LogThrottle(INTERVAL, nanos::get);

    private void advanceSeconds(long seconds) {
        nanos.addAndGet(seconds * 1_000_000_000L);
    }

    @Test
    @DisplayName("첫 호출은 통과시키고 생략 건수는 0이다")
    void allowsFirstCall() {
        assertThat(throttle.acquireOrSuppress()).isZero();
    }

    @Test
    @DisplayName("같은 구간의 뒤이은 호출은 모두 억제한다")
    void suppressesWithinInterval() {
        throttle.acquireOrSuppress();

        assertThat(throttle.acquireOrSuppress()).isEqualTo(LogThrottle.SUPPRESSED);

        advanceSeconds(59);
        assertThat(throttle.acquireOrSuppress()).isEqualTo(LogThrottle.SUPPRESSED);
    }

    @Test
    @DisplayName("간격이 지나면 다시 통과시키고 그동안 억제한 건수를 돌려준다")
    void reportsSuppressedCountAfterInterval() {
        throttle.acquireOrSuppress();

        for (int i = 0; i < 1000; i++) {
            throttle.acquireOrSuppress();
        }

        advanceSeconds(60);

        assertThat(throttle.acquireOrSuppress()).isEqualTo(1000);
    }

    @Test
    @DisplayName("통과한 뒤 생략 건수는 초기화된다")
    void resetsSuppressedCountAfterReporting() {
        throttle.acquireOrSuppress();
        throttle.acquireOrSuppress();

        advanceSeconds(60);
        assertThat(throttle.acquireOrSuppress()).isEqualTo(1);

        advanceSeconds(60);
        assertThat(throttle.acquireOrSuppress()).isZero();
    }

    @Test
    @DisplayName("여러 스레드가 동시에 호출해도 한 번만 통과한다")
    void allowsOnlyOneThreadPerInterval() throws Exception {

        // 세션 3개의 수신 스레드가 같은 쓰로틀을 함께 쓰므로, 경합에서 중복 통과가 없어야 한다.
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        AtomicLong allowed = new AtomicLong();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (throttle.acquireOrSuppress() != LogThrottle.SUPPRESSED){
                        allowed.incrementAndGet();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(allowed.get()).isEqualTo(1);
    }
}
