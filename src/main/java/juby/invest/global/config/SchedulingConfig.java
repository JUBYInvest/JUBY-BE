package juby.invest.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/***
 * @Scheduled 작업이 쓸 스케줄러를 명시한다.
 * <p>
 * 지정하지 않으면 Spring은 컨텍스트에서 TaskScheduler 빈을 찾아 쓴다. 실시간 채팅의 @EnableWebSocketMessageBroker가
 * messageBrokerTaskScheduler 빈을 만들기 때문에, 이 설정이 없으면 일봉 수집 같은 @Scheduled 작업이 채팅 하트비트와
 * 같은 스케줄러를 쓰게 된다. 채팅을 붙이기 전처럼 @Scheduled 전용 스레드 1개를 유지한다.
 * (스레드가 1개라 작업이 겹치면 순서대로 밀린다. 실시간 시세의 재접속·푸시가 별도 스케줄러를 쓰는 이유다.)
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    @Bean
    public ThreadPoolTaskScheduler scheduledJobTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduling-");
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(scheduledJobTaskScheduler());
    }
}
