package juby.invest.domain.kis.realtime.config;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Configuration
public class KisWebSocketConfig {

    /***
     * 복수 체결이 한 프레임에 몰려 오므로 기본 텍스트 버퍼(8KB)로는 부족할 수 있다.
     * 넘치면 프레임이 버려지는 게 아니라 세션이 끊긴다.
     */
    private static final int TEXT_BUFFER_SIZE = 64 * 1024;

    @Bean
    public WebSocketClient kisWebSocketClient() {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(TEXT_BUFFER_SIZE);

        return new StandardWebSocketClient(container);
    }

    /***
     * 재접속 지연 실행 전용 스케줄러.
     * <p>
     * @EnableScheduling이 만드는 기본 TaskScheduler를 쓰지 않는 이유: 그 스케줄러는 스레드가 1개인데
     * DailyPriceScheduler가 종목당 1.2초씩 2분 넘게 점유한다. 재접속을 거기 올리면 일봉 수집이 끝날 때까지
     * 장중 재접속이 밀린다.
     */
    @Bean
    public ScheduledExecutorService realtimeReconnectScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "realtime-reconnect");
            thread.setDaemon(true); // 종료를 붙잡지 않는다
            return thread;
        });
    }
}
