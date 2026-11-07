package juby.invest.domain.livechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import juby.invest.domain.livechat.repository.LiveChatMessageRepository;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.global.security.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/***
 * 실제 서버를 띄우고 STOMP 클라이언트로 붙어, 인증·브로드캐스트·실패 응답·목적지 차단이 함께 맞물리는지 확인한다.
 * 인터셉터, 컨트롤러, 에러 핸들러, 보안 허용 경로가 하나라도 어긋나면 단위 테스트는 통과해도 여기서 깨진다.
 * <p>
 * DB는 다른 @SpringBootTest와 다른 H2 이름을 쓴다. 컨텍스트 캐시로 여러 컨텍스트가 동시에 살아 있을 때
 * create-drop이 서로의 테이블을 지우지 않게 하기 위해서다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:livechat;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
@Import(LiveChatStompIntegrationTest.SchedulingProbeConfig.class)
@DisplayName("실시간 채팅 STOMP 통합")
class LiveChatStompIntegrationTest {

    private static final long TIMEOUT_SECONDS = 5;
    // SUBSCRIBE는 비동기로 브로커에 등록된다. 등록 전에 보낸 메시지는 받지 못하므로 잠시 기다린다.
    private static final long SUBSCRIBE_SETTLE_MILLIS = 300;

    /***
     * @EnableWebSocketMessageBroker가 만드는 messageBrokerTaskScheduler로 @Scheduled 작업이 옮겨 가지 않았는지 본다.
     */
    @TestConfiguration
    static class SchedulingProbeConfig {
        static final CompletableFuture<String> THREAD_NAME = new CompletableFuture<>();

        @Bean
        SchedulingProbe schedulingProbe() {
            return new SchedulingProbe();
        }
    }

    static class SchedulingProbe {
        @Scheduled(fixedDelay = 60_000)
        void run() {
            SchedulingProbeConfig.THREAD_NAME.complete(Thread.currentThread().getName());
        }
    }

    @LocalServerPort private int port;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private MemberRepository memberRepository;
    @Autowired private LiveChatMessageRepository liveChatMessageRepository;
    @Autowired private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();
    private Member writer;
    private Member noNickname;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        writer = memberRepository.save(Member.builder().name("작성자").nickname("차트독학").build());
        noNickname = memberRepository.save(Member.builder().name("닉네임없음").build());
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(session -> {
            if (session.isConnected()) session.disconnect();
        });
        stompClient.stop();
        liveChatMessageRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    private String url() {
        return "ws://localhost:" + port + "/ws/live-chat";
    }

    private String bearer(Member member) {
        return "Bearer " + jwtUtil.createAccessToken(member.getId(), "USER", member.getName());
    }

    private StompSession connect(String authorization, StompSessionHandler handler) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (authorization != null) {
            connectHeaders.add("Authorization", authorization);
        }
        StompSession session = stompClient.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders, handler)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private StompSession connect(String authorization) throws Exception {
        return connect(authorization, new StompSessionHandlerAdapter() {});
    }

    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) throws InterruptedException {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        Thread.sleep(SUBSCRIBE_SETTLE_MILLIS);
        return received;
    }

    // ERROR 프레임의 message 헤더(에러 코드)를 받는 핸들러
    private static class ErrorCapturingHandler extends StompSessionHandlerAdapter {
        final CompletableFuture<String> errorCode = new CompletableFuture<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errorCode.complete(headers.getFirst("message"));
        }
    }

    @Test
    @DisplayName("회원이 보낸 메시지가 비로그인 구독자에게 전달되고 DB에 저장된다")
    void memberMessageIsBroadcastToGuests() throws Exception {
        BlockingQueue<Map<String, Object>> guestInbox = subscribe(connect(null), "/topic/live-chat");

        StompSession writerSession = connect(bearer(writer));
        writerSession.send("/app/live-chat/messages", Map.of("content", "점심시간에 잠깐 들어왔어요"));

        Map<String, Object> message = guestInbox.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.get("writerNickname")).isEqualTo("차트독학");
        assertThat(message.get("content")).isEqualTo("점심시간에 잠깐 들어왔어요");
        assertThat(message.get("messageId")).isNotNull();
        assertThat(liveChatMessageRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("비로그인 사용자가 보내면 본인에게만 LIVECHAT401_1이 가고, 연결은 유지된다")
    void guestCannotSend() throws Exception {
        StompSession guest = connect(null);
        BlockingQueue<Map<String, Object>> errors = subscribe(guest, "/user/queue/errors");
        BlockingQueue<Map<String, Object>> topic = subscribe(guest, "/topic/live-chat");

        guest.send("/app/live-chat/messages", Map.of("content", "안녕하세요"));

        Map<String, Object> error = errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.get("isSuccess")).isEqualTo(false);
        assertThat(error.get("code")).isEqualTo("LIVECHAT401_1");
        assertThat(topic.poll(SUBSCRIBE_SETTLE_MILLIS, TimeUnit.MILLISECONDS)).isNull();
        assertThat(guest.isConnected()).isTrue();
        assertThat(liveChatMessageRepository.count()).isZero();
    }

    @Test
    @DisplayName("닉네임이 없는 회원이 보내면 LIVECHAT403_1이 간다")
    void memberWithoutNicknameCannotSend() throws Exception {
        StompSession session = connect(bearer(noNickname));
        BlockingQueue<Map<String, Object>> errors = subscribe(session, "/user/queue/errors");

        session.send("/app/live-chat/messages", Map.of("content", "안녕하세요"));

        Map<String, Object> error = errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.get("code")).isEqualTo("LIVECHAT403_1");
    }

    @Test
    @DisplayName("실패 응답은 보낸 사람에게만 간다")
    void errorsGoOnlyToSender() throws Exception {
        StompSession other = connect(bearer(writer));
        BlockingQueue<Map<String, Object>> otherErrors = subscribe(other, "/user/queue/errors");

        StompSession sender = connect(bearer(noNickname));
        BlockingQueue<Map<String, Object>> senderErrors = subscribe(sender, "/user/queue/errors");

        sender.send("/app/live-chat/messages", Map.of("content", "안녕하세요"));

        assertThat(senderErrors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        assertThat(otherErrors.poll(SUBSCRIBE_SETTLE_MILLIS, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("무효한 토큰으로는 연결할 수 없고 ERROR 프레임에 에러 코드가 담긴다")
    void rejectsInvalidToken() throws Exception {
        ErrorCapturingHandler handler = new ErrorCapturingHandler();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer not-a-jwt");

        stompClient.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders, handler);

        assertThat(handler.errorCode.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("AUTH401_2");
    }

    @Test
    @DisplayName("클라이언트가 /topic으로 직접 보내면 거부되고 아무에게도 전달되지 않는다")
    void blocksDirectSendToTopic() throws Exception {
        BlockingQueue<Map<String, Object>> guestInbox = subscribe(connect(null), "/topic/live-chat");

        ErrorCapturingHandler handler = new ErrorCapturingHandler();
        StompSession attacker = connect(bearer(writer), handler);
        attacker.send("/topic/live-chat", Map.of("writerNickname", "운영자", "content", "가짜 공지"));

        assertThat(handler.errorCode.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("LIVECHAT403_2");
        assertThat(guestInbox.poll(SUBSCRIBE_SETTLE_MILLIS, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("@Scheduled 작업은 채팅 브로커 스케줄러가 아니라 전용 스케줄러에서 돈다")
    void scheduledJobsDoNotUseBrokerScheduler() throws Exception {
        assertThat(SchedulingProbeConfig.THREAD_NAME.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).startsWith("scheduling-");
    }
}
