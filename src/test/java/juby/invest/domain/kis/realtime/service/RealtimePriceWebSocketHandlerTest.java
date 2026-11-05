package juby.invest.domain.kis.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("실시간 세션 수신 처리기")
class RealtimePriceWebSocketHandlerTest {

    @Mock private WebSocketSession session;

    private RealtimePriceStore priceStore;
    private RealtimeIndexStore indexStore;
    private RealtimePriceWebSocketHandler handler;
    private final List<CloseStatus> closeNotifications = new ArrayList<>();

    @BeforeEach
    void setUp() {
        priceStore = new RealtimePriceStore();
        indexStore = new RealtimeIndexStore();
        closeNotifications.clear();
        handler = new RealtimePriceWebSocketHandler(
                "real", priceStore, indexStore, new ObjectMapper(), closeNotifications::add);
    }

    /*** 체결 프레임 1건. 읽지 않는 자리는 0으로 채운다. */
    private static TextMessage contractFrame(String stockCode, String currentPrice) {
        String[] fields = new String[46];
        Arrays.fill(fields, "0");
        fields[0] = stockCode;
        fields[1] = "093354";
        fields[2] = currentPrice;
        fields[5] = "1.25";
        fields[7] = "70000";
        fields[8] = "74000";
        fields[9] = "69000";
        fields[13] = "1234";
        fields[14] = "5678";
        return new TextMessage("0|H0STCNT0|1|" + String.join("^", fields));
    }

    @Test
    @DisplayName("체결 프레임을 저장소에 반영한다")
    void storesContractFrame() throws Exception {

        handler.handleMessage(session, contractFrame("005930", "73100"));

        assertThat(priceStore.findFresh("005930").orElseThrow().tick().currentPrice()).isEqualTo(73100);
    }

    @Test
    @DisplayName("PINGPONG은 받은 내용 그대로 PONG으로 회신한다")
    void respondsToPingPong() throws Exception {

        // 회신하지 않으면 KIS가 세션을 끊는다.
        String payload = "{\"header\":{\"tr_id\":\"PINGPONG\",\"datetime\":\"20261103093000\"}}";

        handler.handleMessage(session, new TextMessage(payload));

        then(session).should().sendMessage(org.mockito.ArgumentMatchers.any(PongMessage.class));
    }

    @Test
    @DisplayName("등록 성공 응답은 세션을 건드리지 않는다")
    void acceptsSuccessfulSubscriptionResponse() throws Exception {

        String payload = "{\"header\":{\"tr_id\":\"H0STCNT0\",\"tr_key\":\"005930\"},"
                + "\"body\":{\"rt_cd\":\"0\",\"msg1\":\"SUBSCRIBE SUCCESS\"}}";

        handler.handleMessage(session, new TextMessage(payload));

        then(session).should(never()).close();
        then(session).should(never()).close(org.mockito.ArgumentMatchers.any(CloseStatus.class));
    }

    @Test
    @DisplayName("등록이 거부돼도 세션을 끊지 않는다")
    void keepsSessionOnSubscriptionRejection() throws Exception {

        // 한 종목의 등록 실패로 세션을 닫으면 같은 세션이 담당한 나머지 종목까지 멈춘다.
        String payload = "{\"header\":{\"tr_id\":\"H0STCNT0\",\"tr_key\":\"005930\"},"
                + "\"body\":{\"rt_cd\":\"1\",\"msg1\":\"ALREADY IN SUBSCRIBE\"}}";

        handler.handleMessage(session, new TextMessage(payload));

        then(session).should(never()).close();
        then(session).should(never()).close(org.mockito.ArgumentMatchers.any(CloseStatus.class));
    }

    @Test
    @DisplayName("깨진 프레임에도 예외를 올려보내지 않는다")
    void swallowsMalformedFrames() {

        // 예외가 컨테이너까지 올라가면 세션이 닫히고 담당 종목 전체가 멈춘다.
        org.assertj.core.api.Assertions.assertThatCode(() -> {
            handler.handleMessage(session, new TextMessage("{not a json"));
            handler.handleMessage(session, new TextMessage("0|H0STCNT0|1|깨진^데이터"));
            handler.handleMessage(session, new TextMessage(""));
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("암호화 프레임은 무시한다")
    void ignoresEncryptedFrame() throws Exception {

        handler.handleMessage(session, new TextMessage("1|H0STCNI0|1|암호화된내용"));

        assertThat(priceStore.findAllFresh()).isEmpty();
    }

    /*** 지수 프레임 1건. 읽지 않는 자리는 0으로 채운다. */
    private static TextMessage indexFrame(String indexCode, String currentIndex) {
        String[] fields = new String[30];
        Arrays.fill(fields, "0");
        fields[0] = indexCode;
        fields[1] = "133000";
        fields[2] = currentIndex;
        fields[5] = "1234";
        fields[6] = "5678";
        fields[9] = "-0.37";
        fields[10] = "2550.10";
        fields[11] = "2561.44";
        fields[12] = "2538.07";
        return new TextMessage("0|H0UPCNT0|1|" + String.join("^", fields));
    }

    @Test
    @DisplayName("지수 프레임은 지수 저장소로 보낸다")
    void routesIndexFrameToIndexStore() throws Exception {

        handler.handleMessage(session, indexFrame("0001", "2543.21"));

        assertThat(indexStore.findFresh("0001").orElseThrow().tick().currentIndex())
                .isEqualByComparingTo("2543.21");
        // 체결가 저장소는 건드리지 않는다.
        assertThat(priceStore.findAllFresh()).isEmpty();
    }

    @Test
    @DisplayName("체결 프레임은 지수 저장소를 건드리지 않는다")
    void routesContractFrameOnlyToPriceStore() throws Exception {

        handler.handleMessage(session, contractFrame("005930", "73100"));

        assertThat(indexStore.findAllFresh()).isEmpty();
    }

    @Test
    @DisplayName("구독하지 않은 TR의 프레임은 어느 저장소에도 넣지 않는다")
    void ignoresUnknownTr() throws Exception {

        String[] fields = new String[30];
        Arrays.fill(fields, "0");

        handler.handleMessage(session, new TextMessage("0|H0STASP0|1|" + String.join("^", fields)));

        assertThat(priceStore.findAllFresh()).isEmpty();
        assertThat(indexStore.findAllFresh()).isEmpty();
    }

    @Test
    @DisplayName("세션이 닫히면 매니저에 사유를 전달한다")
    void notifiesManagerOnClose() {

        handler.afterConnectionClosed(session, CloseStatus.SERVER_ERROR);

        assertThat(closeNotifications).containsExactly(CloseStatus.SERVER_ERROR);
    }
}
