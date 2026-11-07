package juby.invest.domain.livechat.config;

/***
 * 실시간 채팅 STOMP 경로. 프론트와의 계약이므로 바꿀 때는 프론트도 함께 바꿔야 한다.
 */
public final class LiveChatDestinations {

    // 웹소켓 연결 주소 (핸드셰이크)
    public static final String ENDPOINT = "/ws/live-chat";

    public static final String APP_PREFIX = "/app";
    public static final String TOPIC_PREFIX = "/topic";
    public static final String QUEUE_PREFIX = "/queue";
    public static final String USER_PREFIX = "/user";

    // 메시지 전송 (@MessageMapping은 APP_PREFIX를 뺀 SEND_MAPPING을 쓴다)
    public static final String SEND_MAPPING = "/live-chat/messages";
    public static final String SEND = APP_PREFIX + SEND_MAPPING;

    // 모든 사람이 구독하는 채팅방
    public static final String TOPIC = TOPIC_PREFIX + "/live-chat";

    // 보낸 사람에게만 가는 실패 응답. 클라이언트는 USER_ERRORS를 구독하고, 서버는 ERRORS로 보낸다.
    public static final String ERRORS = QUEUE_PREFIX + "/errors";
    public static final String USER_ERRORS = USER_PREFIX + ERRORS;

    private LiveChatDestinations() {
    }
}
