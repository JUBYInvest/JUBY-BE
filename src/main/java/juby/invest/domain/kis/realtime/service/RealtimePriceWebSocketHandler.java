package juby.invest.domain.kis.realtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import juby.invest.domain.kis.realtime.converter.RealtimeFrame;
import juby.invest.domain.kis.realtime.converter.RealtimeFrameSplitter;
import juby.invest.domain.kis.realtime.converter.RealtimeIndexParser;
import juby.invest.domain.kis.realtime.converter.RealtimeTickParser;
import juby.invest.global.logging.ThrottledLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/***
 * KIS 실시간 세션 1개의 수신 처리기.
 * <p>
 * 세션마다 담당 종목과 로그 식별자가 다르므로 세션별로 새로 만든다. 그래서 @Component가 아니다.
 * (SecurityConfig가 JwtAuthenticationFilter를 직접 new 하는 것과 같은 이유 - 빈으로 만들면 안 되는 쪽이다)
 * <p>
 * 체결가(H0STCNT0)와 지수(H0UPCNT0)가 같은 세션으로 함께 들어오므로, 프레임의 tr_id로 갈라 각자의
 * 저장소에 넣는다.
 * <p>
 * 프레임 종류는 첫 글자로 갈린다.
 * <ul>
 *     <li>'0' : 평문 실시간 데이터 (체결가)</li>
 *     <li>'1' : 암호화된 실시간 데이터 (체결통보 전용. 체결가에는 오지 않는다)</li>
 *     <li>그 외 : JSON 제어 프레임 (PINGPONG, 등록 응답)</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class RealtimePriceWebSocketHandler extends TextWebSocketHandler {

    private static final String PING_PONG = "PINGPONG";
    private static final char PLAIN_DATA = '0';
    private static final char ENCRYPTED_DATA = '1';
    private static final String SUCCESS_RETURN_CODE = "0";

    /*** 로그 식별자. 세션이 여러 개이므로 어느 세션의 로그인지 구분할 수단이 필요하다. (앱키는 절대 로깅하지 않는다) */
    private final String sessionName;
    private final RealtimePriceStore priceStore;
    private final RealtimeIndexStore indexStore;
    private final ObjectMapper objectMapper;

    /*** 세션이 닫혔을 때 세션 매니저에 알리는 통로. 재접속 판단은 매니저가 한다. */
    private final Consumer<CloseStatus> closeListener;

    /***
     * 프레임 단위 경고는 틱마다 발생할 수 있으므로 60초에 1줄로 줄인다.
     * 세션마다 핸들러가 따로 만들어지므로 쓰로틀도 세션별로 독립이다.
     */
    private final ThrottledLogger encryptedFrameLog =
            new ThrottledLogger(RealtimePriceWebSocketHandler.class, Duration.ofSeconds(60));
    private final ThrottledLogger frameFailureLog =
            new ThrottledLogger(RealtimePriceWebSocketHandler.class, Duration.ofSeconds(60));
    private final ThrottledLogger unknownTrLog =
            new ThrottledLogger(RealtimePriceWebSocketHandler.class, Duration.ofSeconds(60));

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("실시간 세션이 열렸습니다. 세션: {}", sessionName);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {

        String payload = message.getPayload();
        if (payload == null || payload.isEmpty()){
            return;
        }

        // 프레임 1건의 처리 실패로 예외가 컨테이너까지 올라가면 세션이 닫히고,
        // 그 세션이 담당한 종목 전체가 멈춘다. 여기서 모두 받아낸다.
        try {
            char type = payload.charAt(0);

            // 암호화 유무: 0 암호화 되지 않은 데이터 / 1 암호화된 데이터
            if (type == PLAIN_DATA){
                route(payload);
            } else if (type == ENCRYPTED_DATA){
                encryptedFrameLog.warn("체결가 세션에 암호화 프레임이 수신되어 무시합니다. 세션: {}", sessionName);
            } else {
                handleControlFrame(session, payload);
            }
        } catch (Exception e) {
            frameFailureLog.warn("실시간 프레임 처리에 실패했습니다. 세션: {}", sessionName, e);
        }
    }

    /***
     * 실시간 데이터 프레임을 TR에 따라 알맞은 파서/저장소로 보낸다.
     * 프레임 분해는 한 번만 하고, 파서는 분해된 프레임을 받는다.
     */
    private void route(String payload) {

        RealtimeFrameSplitter.split(payload).ifPresent(frame -> {
            switch (frame.trId()) {
                case RealtimeTickParser.TR_ID -> RealtimeTickParser.parse(frame).forEach(priceStore::put);
                case RealtimeIndexParser.TR_ID -> RealtimeIndexParser.parse(frame).forEach(indexStore::put);

                // 구독하지 않은 TR이 섞여 들어와도 조용히 버린다.
                default -> unknownTrLog.warn("구독하지 않은 TR의 실시간 프레임을 무시합니다. 세션: {}, tr_id: {}",
                        sessionName, frame.trId());
            }
        });
    }

    /***
     * JSON 제어 프레임을 처리한다.
     * PINGPONG 판정을 가장 먼저 한다. 회신하지 않으면 KIS가 세션을 끊는다.
     */
    private void handleControlFrame(WebSocketSession session, String payload) throws Exception {

        JsonNode root = objectMapper.readTree(payload);
        JsonNode header = root.path("header");
        String trId = header.path("tr_id").asText();

        if (PING_PONG.equals(trId)){
            // 받은 payload를 그대로 PONG으로 돌려준다. (KIS 공식 샘플의 websocket.pong(data)와 같은 동작)
            session.sendMessage(new PongMessage(ByteBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8))));
            return;
        }

        JsonNode body = root.path("body");
        String returnCode = body.path("rt_cd").asText();
        String stockCode = header.path("tr_key").asText();

        if (SUCCESS_RETURN_CODE.equals(returnCode)){
            // 종목당 1건씩 오므로 INFO로 두면 연결마다 100줄이 쌓인다. 집계는 세션 매니저가 남긴다.
            log.debug("실시간 등록 완료. 세션: {}, 종목: {}", sessionName, stockCode);
            return;
        }

        // 한 종목의 등록 실패로 세션을 끊으면 같은 세션이 담당한 나머지 종목까지 멈춘다. 세션은 유지한다.
        log.warn("실시간 등록이 거부되었습니다. 세션: {}, 종목: {}, 사유: {}",
                sessionName, stockCode, body.path("msg1").asText());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        // 전송 오류 후 컨테이너가 세션을 닫으면 afterConnectionClosed에서 재접속이 걸린다. 여기서는 기록만 한다.
        log.warn("실시간 세션 전송 오류. 세션: {}", sessionName, exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeListener.accept(status);
    }
}
