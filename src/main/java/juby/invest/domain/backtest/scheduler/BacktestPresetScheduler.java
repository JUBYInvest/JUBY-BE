package juby.invest.domain.backtest.scheduler;

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

    // 일봉 수집(평일 22:10)이 확실히 반영되고, 사용자 트래픽이 가장 적은 새벽 시간대(오전 04:00) 에 실행
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void recalculatePresetResults() {
        log.info("[스케줄러-2] 백테스트 프리셋 재계산 스케줄러 동작 시작.");
        backtestPresetService.recalculateAll();
        log.info("[스케줄러-2] 백테스트 프리셋 재계산 스케줄러 동작 완료.");
    }
}
