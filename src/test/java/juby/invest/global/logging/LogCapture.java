package juby.invest.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/***
 * 테스트용 로그 수집기. 로그 이벤트와 **그 시점의 MDC 스냅샷**을 함께 보관한다.
 *
 * MdcLoggingFilter / SchedulerLoggingAspect는 로그를 남긴 **직후** MDC.clear()를 호출하므로,
 * 테스트가 나중에 MDC를 읽으면 이미 비어 있다. ILoggingEvent.getMDCPropertyMap()은 지연 평가라
 * 호출 시점에 따라 값이 달라질 수 있어 신뢰할 수 없다. 그래서 append 시점에 직접 떠둔다.
 */
final class LogCapture extends AppenderBase<ILoggingEvent> {

    private final List<ILoggingEvent> events = new ArrayList<>();
    private final List<Map<String, String>> mdcSnapshots = new ArrayList<>();
    private final Logger target;

    private LogCapture(Logger target) {
        this.target = target;
    }

    static LogCapture attachTo(Class<?> loggerOwner) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerOwner);
        LogCapture capture = new LogCapture(logger);
        capture.start();
        logger.addAppender(capture);
        return capture;
    }

    void detach() {
        target.detachAppender(this);
        stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        events.add(event);
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        mdcSnapshots.add(mdc == null ? Map.of() : Map.copyOf(mdc));
    }

    List<ILoggingEvent> events() {
        return events;
    }

    ILoggingEvent lastEvent() {
        return events.get(events.size() - 1);
    }

    /** 마지막 로그가 남은 시점의 MDC. */
    Map<String, String> lastMdc() {
        return mdcSnapshots.get(mdcSnapshots.size() - 1);
    }
}