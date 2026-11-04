package juby.invest.global.scheduler;

import juby.invest.domain.kis.market.service.MarketService;
import juby.invest.domain.kis.realtime.service.RealtimeSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/***
 * 실시간 세션의 연결/종료 시각을 관리한다.
 * <p>
 * EC2 단일 컨테이너에서만 돌려야 해서 dev 프로파일로 제한한다. appkey 1개당 웹소켓 세션이 1개뿐이므로,
 * 인스턴스가 2대가 되면 서로의 세션을 끊는다. <b>스케일아웃 전에 세션 소유권 설계가 먼저 필요하다.</b>
 * <p>
 * 이 클래스가 global/scheduler 아래에 있어야 하는 이유: SchedulerLoggingAspect의 포인트컷이
 * 애노테이션이 아니라 <b>패키지 기준</b>이라, 다른 위치에 두면 jobName/runId 태깅이 붙지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("dev") // EC2에서만 실행
public class RealtimeSessionScheduler {

    private final RealtimeSessionManager sessionManager;
    private final MarketService marketService;

    /***
     * 장 시작(09:00) 전에 세션을 열고 종목을 등록한다.
     * 등록을 미리 끝내야 개장 직후 첫 체결부터 받을 수 있다.
     * 참고: cron zone 미지정 -> JVM 기본 시간대 (Dockerfile의 -Duser.timezone) 사용
     */
    @Scheduled(cron = "0 55 8 * * MON-FRI")
    public void connect() throws InterruptedException {

        LocalDate today = LocalDate.now();

        if (marketService.isClosedDay(today)){
            log.info("금일({})은 휴장일이어서 실시간 세션을 열지 않습니다", today);
            return;
        }

        sessionManager.connect();
    }

    /***
     * 장 종료(15:30) 후 세션을 닫는다.
     * 장외 시간에 세션을 붙들고 있을 이유가 없고, 붙들고 있으면 같은 appkey로 다른 작업을 할 수도 없다.
     * 휴장일 판별을 하지 않는 이유: 열지 않았으면 종료는 아무 일도 하지 않는다.
     */
    @Scheduled(cron = "0 40 15 * * MON-FRI")
    public void disconnect() {
        sessionManager.disconnect();
    }
}
