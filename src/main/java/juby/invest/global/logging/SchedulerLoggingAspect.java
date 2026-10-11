package juby.invest.global.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Aspect
@Slf4j
public class SchedulerLoggingAspect {

    @Around("execution(* juby.invest..global.scheduler..*.*(..))") // 모든 스케쥴러에 대해서 AOP 도입
    public Object around(ProceedingJoinPoint pjp) throws Throwable {

        MDC.put("jobName", pjp.getSignature().getDeclaringType().getSimpleName());
        MDC.put("runId", UUID.randomUUID().toString().substring(0, 8));

        // 스케줄러 시작시간
        long start = System.nanoTime();

        try {
            Object r = pjp.proceed();
            MDC.put("jobResult", "SUCCESS");
            return r;
        } catch (Throwable t){
            MDC.put("jobResult", "FAILURE");
            throw t;
        } finally {
            long latency = (System.nanoTime() - start) / 1_000_000; // ms단위로 나타내기 위해 나눠준다.

            MDC.put("jobLatencyMs", String.valueOf(latency));
            log.info("스케줄러 종료");
            MDC.clear();
        }
    }
}
