package juby.invest.global.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("쓰로틀 WARN 로거")
class ThrottledLoggerTest {

    private static final Duration INTERVAL = Duration.ofSeconds(60);

    private final AtomicLong nanos = new AtomicLong(-5_000_000_000L);

    private LogCapture capture;
    private ThrottledLogger throttledLogger;

    @BeforeEach
    void setUp() {
        capture = LogCapture.attachTo(ThrottledLoggerTest.class);
        throttledLogger = new ThrottledLogger(
                LoggerFactory.getLogger(ThrottledLoggerTest.class),
                INTERVAL,
                new LogThrottle(INTERVAL, nanos::get));
    }

    @AfterEach
    void tearDown() {
        capture.detach();
    }

    private void advanceSeconds(long seconds) {
        nanos.addAndGet(seconds * 1_000_000_000L);
    }

    @Test
    @DisplayName("구간의 첫 로그는 생략 문구 없이 그대로 남는다")
    void logsFirstMessageAsIs() {

        throttledLogger.warn("종목코드: {}", "005930");

        assertThat(capture.events()).hasSize(1);
        assertThat(capture.lastEvent().getFormattedMessage()).isEqualTo("종목코드: 005930");
    }

    @Test
    @DisplayName("같은 구간의 뒤이은 로그는 남기지 않는다")
    void suppressesWithinInterval() {

        for (int i = 0; i < 500; i++) {
            throttledLogger.warn("종목코드: {}", "005930");
        }

        assertThat(capture.events()).hasSize(1);
    }

    @Test
    @DisplayName("간격이 지나면 생략 건수를 문구로 덧붙여 남긴다")
    void appendsSuppressedCount() {

        throttledLogger.warn("종목코드: {}", "005930");
        for (int i = 0; i < 499; i++) {
            throttledLogger.warn("종목코드: {}", "005930");
        }

        advanceSeconds(60);
        throttledLogger.warn("종목코드: {}", "005930");

        assertThat(capture.events()).hasSize(2);
        assertThat(capture.lastEvent().getFormattedMessage())
                .isEqualTo("종목코드: 005930 (직전 60초간 같은 사유 499건 생략)");
    }

    @Test
    @DisplayName("마지막 인자가 예외면 생략 문구를 붙여도 스택트레이스가 유지된다")
    void keepsThrowableWhenAppendingSuppressedCount() {

        // SLF4J는 마지막 인자만 Throwable로 인식한다. 생략 건수를 뒤에 붙이면 스택트레이스가 사라진다.
        IllegalStateException cause = new IllegalStateException("프레임 깨짐");

        throttledLogger.warn("세션: {}", "real", cause); // 남는다
        throttledLogger.warn("세션: {}", "real", cause); // 억제 1
        throttledLogger.warn("세션: {}", "real", cause); // 억제 2

        advanceSeconds(60);
        throttledLogger.warn("세션: {}", "real", cause);

        ILoggingEvent lastEvent = capture.lastEvent();
        assertThat(lastEvent.getFormattedMessage()).isEqualTo("세션: real (직전 60초간 같은 사유 2건 생략)");
        assertThat(lastEvent.getThrowableProxy()).isNotNull();
        assertThat(lastEvent.getThrowableProxy().getMessage()).isEqualTo("프레임 깨짐");
    }

    @Test
    @DisplayName("예외만 넘긴 첫 로그도 스택트레이스를 남긴다")
    void keepsThrowableOnFirstLog() {

        throttledLogger.warn("세션: {}", "real", new IllegalStateException("프레임 깨짐"));

        assertThat(capture.lastEvent().getThrowableProxy()).isNotNull();
    }
}
