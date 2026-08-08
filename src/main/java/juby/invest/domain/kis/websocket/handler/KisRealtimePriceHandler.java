package juby.invest.domain.kis.websocket.handler;

import juby.invest.domain.kis.websocket.dto.RealtimePriceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisRealtimePriceHandler extends TextWebSocketHandler {

    private static final String PING_PONG = "PINGPONG";
    private static final String REALTIME_PRICE_TR_ID = "H0STCNT0";
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("[KIS Websocket] 연결 성공 (sessionId = {})", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        //  2. 실시간 결과 응답 ( | 로 구분되는 값)
        //  ex) 0|H0STCNT0|004|005930^123929^73100^5^...
        //  - 암호화 유무 : 0 암호화 되지 않은 데이터 / 1 암호화된 데이터
        //  - TR_ID : 등록한 tr_id (ex. H0STCNT0)
        //          - 데이터 건수 : (ex. 001 인 경우 데이터 건수 1건, 004인 경우 데이터 건수 4건)
        //  - 응답 데이터 : 아래 response 데이터 참조 ( ^로 구분됨)
        //  ※ 데이터가 많은 경우 여러 건을 페이징 처리해서 데이터를 보내는 점 참고 부탁드립니다.
        //  ex) 0|H0STCNT0|004|... 인 경우 004가 데이터 개수를 의미하여, 뒤에 체결데이터가 4건 들어옴
        //   → 0|H0STCNT0|004|005930^123929...(체결데이터1)...^005930^123929...(체결데이터2)...^005930^123929...(체결데이터3)...^005930^123929...(체결데이터4)...
        String payload = message.getPayload();

        if (payload.startsWith(PING_PONG)){
            session.sendMessage(new TextMessage(PING_PONG));
            return;
        }

        if (payload.startsWith("0|") || payload.startsWith("1|")){
            handleRealtimeFrame(payload);
            return;
        }

        log.info("[KIS Websocket] 제어 메시지 수신: {}", payload);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("[KIS Websocket] 전송 오류 (sessionId = {})", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("[KIS Websocket] 연결 종료 (sessionId = {}, status = {}", session.getId(), status);
    }

    /***
     * 함수 기능: 응답데이터를 파싱한다. 어떤게 들어있지?
     * @param payload
     */
    private void handleRealtimeFrame(String payload) {
        String[] frame = payload.split("\\|", 4);
        String trId = frame[1];

        if (!trId.equals(REALTIME_PRICE_TR_ID)){
            return;
        }

        int dataCount = Integer.parseInt(frame[2]);
        String[] fields = frame[3].split("\\^"); // [005930, 123929, 005930, 123929, ...]
        int fieldsPerRow = fields.length / dataCount; // 예) 데이터 건수 4건 -> 8개로 나뉘어짐

        // fields[0, 2] , fields[2, 4], ...
        for (int i = 0; i < dataCount; i++){
            String[] row = Arrays.copyOfRange(fields, i * fieldsPerRow, (i + 1) * fieldsPerRow);
            eventPublisher.publishEvent(new RealTimeReceivedEvent(RealtimePriceDto.RealtimePriceRes.from(row)));
        }
    }
}
