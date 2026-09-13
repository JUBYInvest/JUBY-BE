package juby.invest.global.scheduler;

import juby.invest.domain.auth.repository.AccessTokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Profile("dev") // ec2에서만 실행
@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteExpiredATScheduler {

    private final AccessTokenBlacklistRepository accessTokenBlacklistRepository;

    @Scheduled(cron = "0 0 0 1/7 * *")
    @Transactional
    public void deleteExpiredAT(){

        try {
            int deleteCnt = accessTokenBlacklistRepository.deleteExpired(LocalDateTime.now());
            log.info("[스케줄러-4] 블랙리스트 AT {}건 제거", deleteCnt);
        } catch (Exception e){
            log.warn("[스케줄러-4] 블랙리스트 AT 정리 중 문제 발생", e);
        }
    }
}
