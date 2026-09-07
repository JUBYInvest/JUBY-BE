package juby.invest.global.scheduler;

import juby.invest.domain.auth.entity.AccessTokenBlacklist;
import juby.invest.domain.auth.repository.AccessTokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Profile("dev") // ec2에서만 실행
@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteExpiredATScheduler {

    private final AccessTokenBlacklistRepository accessTokenBlacklistRepository;

    @Scheduled(cron = "0 0 0 1 * *")
    public void deleteExpiredAT(){
        LocalDateTime now = LocalDateTime.now();
        List<AccessTokenBlacklist> expiredAT = accessTokenBlacklistRepository.findAllByExpiresAtBefore(now);

        try {
            accessTokenBlacklistRepository.deleteAll(expiredAT);
            log.info("[스케줄러-4] 만료된 AT를 전부 제거했습니다.");
        } catch (Exception e){
            log.warn("[스케줄러-4] 만료된 AT를 제거하는 스케줄러 동작 중 문제 발생");
        }
    }
}
