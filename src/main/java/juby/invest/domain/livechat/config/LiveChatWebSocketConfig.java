package juby.invest.domain.livechat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/***
 * 홈 화면 실시간 채팅용 STOMP 서버 설정.
 * <p>
 * KIS 실시간 시세의 웹소켓(KisWebSocketConfig)은 우리 서버가 KIS에 붙는 <b>클라이언트</b>이고,
 * 이 설정은 브라우저가 우리 서버에 붙는 <b>서버</b> 쪽이다. 둘은 관계가 없다.
 * <p>
 * 브로커는 Spring 내장 SimpleBroker다. 단일 인스턴스로만 운영하므로 충분하고, 인스턴스를 늘리면
 * 서로 다른 인스턴스에 붙은 사용자끼리 메시지가 안 보이므로 외부 브로커(Redis/RabbitMQ)로 바꿔야 한다.
 * <p>
 * 이 설정이 만드는 messageBrokerTaskScheduler 빈 때문에 @Scheduled 작업이 그 스케줄러로 옮겨 가지 않도록
 * global/config/SchedulingConfig에서 @Scheduled 전용 스케줄러를 지정해 둔다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class LiveChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /***
     * 하트비트 주기(ms). {서버가 보내는 주기, 클라이언트에게 기대하는 주기}.
     * nginx의 proxy_read_timeout(기본 60초) 동안 아무 프레임도 없으면 연결이 끊기므로 그보다 짧게 둔다.
     */
    private static final long[] HEARTBEAT = {10_000, 10_000};

    private final LiveChatChannelInterceptor liveChatChannelInterceptor;
    private final LiveChatStompErrorHandler liveChatStompErrorHandler;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    private TaskScheduler messageBrokerTaskScheduler;

    // 이 설정 클래스가 만드는 빈을 이 클래스에 주입하므로 @Lazy로 순환 참조를 피한다. (Spring 문서의 방식)
    @Autowired
    public void setMessageBrokerTaskScheduler(@Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler taskScheduler) {
        this.messageBrokerTaskScheduler = taskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 같은 출처(서버의 테스트 페이지)는 별도 설정 없이 허용된다. 프론트 출처는 WebMvcConfig의 CORS와 맞춘다.
        registry.addEndpoint(LiveChatDestinations.ENDPOINT)
                .setAllowedOrigins("http://localhost:5173", allowedOrigins);
        registry.setErrorHandler(liveChatStompErrorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(LiveChatDestinations.TOPIC_PREFIX, LiveChatDestinations.QUEUE_PREFIX)
                .setHeartbeatValue(HEARTBEAT)
                .setTaskScheduler(messageBrokerTaskScheduler);
        registry.setApplicationDestinationPrefixes(LiveChatDestinations.APP_PREFIX);
        registry.setUserDestinationPrefix(LiveChatDestinations.USER_PREFIX);
        // 같은 세션으로 나가는 메시지의 순서를 보장한다. (기본값은 스레드 풀에서 순서가 섞일 수 있다)
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(liveChatChannelInterceptor);
    }
}
