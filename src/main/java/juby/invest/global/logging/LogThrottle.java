package juby.invest.global.logging;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/***
 * 같은 사유로 쏟아지는 로그를 일정 간격마다 1줄로 줄인다.
 * <p>
 * 틱 단위로 들어오는 경로(실시간 체결 프레임 처리 등)에서 형식이 한 번 어긋나면 초당 수백~수천 줄이 쌓인다.
 * 로그가 많아지는 것 자체도 문제지만, 수집 파이프라인 비용과 다른 로그의 가시성까지 함께 잃는다.
 * <p>
 * 생략한 건수를 함께 돌려주므로, 남긴 1줄만 봐도 그 구간에 몇 건이 더 있었는지 알 수 있다.
 * 여러 스레드(세션 3개의 수신 스레드)가 동시에 호출하므로 CAS로 한 스레드만 통과시킨다.
 */
public class LogThrottle {

    /*** 로그를 남기지 않아야 함을 뜻하는 반환값 */
    public static final long SUPPRESSED = -1;

    private final long intervalNanos;
    private final LongSupplier nanoClock;

    private final AtomicLong nextAllowedAt;
    private final AtomicLong suppressedCount = new AtomicLong();

    public LogThrottle(Duration interval) {
        this(interval, System::nanoTime);
    }

    // 간격 경계를 테스트에서 대기 없이 검증하기 위한 생성자
    LogThrottle(Duration interval, LongSupplier nanoClock) {
        this.intervalNanos = interval.toNanos();
        this.nanoClock = nanoClock;

        // System.nanoTime()의 기준점은 임의이고 음수일 수도 있다. 0으로 두면 첫 호출이 통과하지 못할 수 있으므로
        // 생성 시각으로 초기화해 첫 호출은 항상 통과시킨다.
        this.nextAllowedAt = new AtomicLong(nanoClock.getAsLong());
    }

    /***
     * 로그를 남겨야 하는지 판단한다.
     *
     * @return 남겨야 하면 직전 구간에서 생략한 건수(0 이상), 남기지 않아야 하면 {@link #SUPPRESSED}
     */
    public long acquireOrSuppress() {

        long now = nanoClock.getAsLong();
        long next = nextAllowedAt.get();

        // nanoTime은 오버플로가 가능하므로 값끼리 비교하지 않고 차이의 부호를 본다.
        if (now - next >= 0 && nextAllowedAt.compareAndSet(next, now + intervalNanos)){
            return suppressedCount.getAndSet(0);
        }

        suppressedCount.incrementAndGet();
        return SUPPRESSED;
    }
}
