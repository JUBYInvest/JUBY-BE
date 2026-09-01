package juby.invest.global.scheduler;

import juby.invest.domain.backtest.service.BacktestPresetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@Profile("dev") // EC2에서만 실행
public class BacktestPresetScheduler {

    private final BacktestPresetService backtestPresetService;

    /***
     * 스케줄러 동작 시각: 일봉 수집(평일 16:00)이 확실히 반영되고, 사용자 트래픽이 가장 적은 새벽 시간대(오전 04:00) 에 실행
     * 수행 동작: 백테스트 프리셋 재계산을 수행한다.
     * 참고: cron zone 미지정 -> JVM 기본 시간대 (Dockerfile의 -Duser.timezone) 사용
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void recalculatePresetResults() {
        log.info("[스케줄러-2] 백테스트 프리셋 재계산 스케줄러 동작 시작.");
        backtestPresetService.recalculateAll();
        log.info("[스케줄러-2] 백테스트 프리셋 재계산 스케줄러 동작 완료.");
    }
}
