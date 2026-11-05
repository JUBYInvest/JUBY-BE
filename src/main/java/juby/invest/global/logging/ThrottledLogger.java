package juby.invest.global.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/***
 * {@link LogThrottle}을 적용한 WARN 로거. 억제한 건수를 메시지 끝에 덧붙여 남긴다.
 * <p>
 * 틱마다 호출되는 경로(실시간 프레임 처리)에서 같은 사유가 쏟아질 때 쓴다.
 * 쓰로틀 판정 + "생략 N건" 문구를 매 호출 지점마다 직접 쓰면 금방 복붙 코드가 되므로 여기로 모았다.
 */
public class ThrottledLogger {

    private final Logger log;
    private final LogThrottle throttle;
    private final long intervalSeconds;

    public ThrottledLogger(Class<?> owner, Duration interval) {
        this(LoggerFactory.getLogger(owner), interval, new LogThrottle(interval));
    }

    // 간격 경계를 테스트에서 대기 없이 검증하기 위한 생성자
    ThrottledLogger(Logger log, Duration interval, LogThrottle throttle) {
        this.log = log;
        this.throttle = throttle;
        this.intervalSeconds = interval.toSeconds();
    }

    /***
     * 같은 사유의 로그를 간격당 1줄만 남긴다. 억제된 구간이 있었다면 그 건수를 함께 적는다.
     * 인자 목록 마지막에 Throwable을 넘기면 스택트레이스가 그대로 남는다.
     */
    public void warn(String message, Object... args) {

        long suppressed = throttle.acquireOrSuppress();

        if (suppressed == LogThrottle.SUPPRESSED){
            return;
        }

        if (suppressed > 0){
            log.warn(message + " (직전 {}초간 같은 사유 {}건 생략)", withSuppressedCount(args, suppressed));
            return;
        }

        log.warn(message, args);
    }

    /***
     * 생략 건수 인자를 끼워 넣는다.
     * <p>
     * SLF4J는 <b>마지막</b> 인자가 Throwable이면 스택트레이스로 처리한다. 그래서 뒤에 그냥 덧붙이면
     * 예외가 평범한 인자로 밀려나 스택트레이스가 사라진다. Throwable이 있으면 그 앞에 끼운다.
     */
    private Object[] withSuppressedCount(Object[] args, long suppressed) {

        boolean endsWithThrowable = args.length > 0 && args[args.length - 1] instanceof Throwable;
        int insertAt = endsWithThrowable ? args.length - 1 : args.length;

        Object[] merged = new Object[args.length + 2];
        System.arraycopy(args, 0, merged, 0, insertAt);
        merged[insertAt] = intervalSeconds;
        merged[insertAt + 1] = suppressed;

        if (endsWithThrowable){
            merged[merged.length - 1] = args[args.length - 1];
        }
        return merged;
    }
}
