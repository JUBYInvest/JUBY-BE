package juby.invest.domain.kis.realtime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class RealtimeStreamConfig {

    /***
     * SSE 브로드캐스트·하트비트 전용 스케줄러.
     * <p>
     * <b>@Scheduled를 쓰지 않는 이유</b>: SchedulerLoggingAspect의 포인트컷이 패키지 기준
     * (global.scheduler..*)이라, 1초 주기 작업을 거기 두면 "스케줄러 종료" INFO가 초당 1줄,
     * 하루 86,400줄 쌓인다. 수집 비용도 비용이지만 다른 로그가 묻힌다.
     * <p>
     * 기본 TaskScheduler를 쓰지 않는 이유는 실시간 재접속 스케줄러와 같다. 스레드가 1개인데
     * DailyPriceScheduler가 2분 넘게 점유하는 동안 푸시가 통째로 밀린다.
     */
    @Bean
    public ScheduledExecutorService realtimeStreamScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "realtime-stream");
            thread.setDaemon(true); // 종료를 붙잡지 않는다
            return thread;
        });
    }
}
