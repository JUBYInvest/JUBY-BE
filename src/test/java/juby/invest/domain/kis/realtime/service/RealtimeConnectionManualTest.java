package juby.invest.domain.kis.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import juby.invest.domain.kis.realtime.config.KisWebSocketProperties;
import juby.invest.domain.kis.realtime.converter.RealtimeTickParser;
import juby.invest.domain.kis.realtime.dto.RealtimePrice;
import juby.invest.domain.kis.realtime.dto.RealtimeSubscription;
import juby.invest.domain.kis.token.service.TokenService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KIS 실시간 세션 수동 검증.
 * <p>
 * <b>실제 KIS 서버에 접속하므로 CI/일반 테스트 실행에서는 제외한다(@Disabled).</b>
 * 장중(09:00~15:30)에 IDE에서 개별 실행해서 쓴다. 로컬 .env의 앱키 3쌍과, DB에 적재된 종목이 필요하다.
 * <p>
 * 이 테스트가 닫으려는 미확인 항목(이슈 #113):
 * <ol>
 *     <li>서로 다른 appkey 3개로 세션 3개를 <b>동시에</b> 유지할 수 있는가</li>
 *     <li>모의투자 세션이 내려주는 시세가 실전과 동일한 실제 시세인가</li>
 * </ol>
 * 성공/실패 여부만으로 판단할 수 있게, 수신 결과를 단정문 메시지에 실어 둔다.
 */
@Disabled("수동 검증용. 실제 KIS 웹소켓에 접속하므로 장중에 직접 실행한다")
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("[수동] KIS 실시간 세션 검증")
class RealtimeConnectionManualTest {

    /*** 틱이 쌓이기를 기다리는 시간. 유동성 높은 종목이면 수 초 안에 들어온다. */
    private static final Duration RECEIVE_WINDOW = Duration.ofSeconds(30);

    /*** 모의/실전 시세 비교용 종목 (삼성전자 - 체결이 가장 잦다) */
    private static final String COMPARE_TARGET = "005930";

    @Autowired private RealtimeSessionManager sessionManager;
    @Autowired private RealtimePriceStore store;
    @Autowired private KisWebSocketProperties properties;
    @Autowired private TokenService tokenService;
    @Autowired private WebSocketClient kisWebSocketClient;
    @Autowired private ObjectMapper objectMapper;

    private List<KisWebSocketProperties.Credential> configuredCredentials() {
        return properties.credentials().stream()
                .filter(KisWebSocketProperties.Credential::isConfigured)
                .toList();
    }

    @Test
    @DisplayName("세션 3개를 동시에 유지하며 전 종목 틱을 받는다")
    void receivesTicksFromAllSessions() throws Exception {

        assertThat(configuredCredentials())
                .as("설정된 자격증명 (.env의 앱키 3쌍을 확인)")
                .hasSize(3);

        try {
            sessionManager.connect();
            Thread.sleep(RECEIVE_WINDOW.toMillis());

            Map<String, RealtimePrice> received = store.findAllFresh();

            // 세션 중 하나라도 거부됐다면 그 세션 담당 종목(약 33개)이 통째로 비어 있다.
            assertThat(received.size())
                    .as("%s초 동안 수신한 종목 수. 70종목 미만이면 세션 하나가 붙지 못한 것이다. 수신 종목: %s",
                            RECEIVE_WINDOW.toSeconds(), received.keySet())
                    .isGreaterThanOrEqualTo(70);

        } finally {
            sessionManager.disconnect();
        }
    }

    @Test
    @DisplayName("모의 세션과 실전 세션의 시세가 일치한다")
    void mockSessionServesSameQuoteAsReal() throws Exception {

        Map<String, RealtimePrice> lastPriceByCredential = new LinkedHashMap<>();
        List<WebSocketSession> sessions = new ArrayList<>();

        try {
            // 자격증명마다 세션을 따로 열고, 같은 종목 1건만 구독해 서로의 시세를 비교한다.
            for (KisWebSocketProperties.Credential credential : configuredCredentials()) {

                RealtimePriceStore perSessionStore = new RealtimePriceStore();
                String approvalKey = tokenService.issueApprovalKey(
                        credential.restBaseUrl(), credential.appKey(), credential.appSecret());

                WebSocketSession session = kisWebSocketClient.execute(
                                new RealtimePriceWebSocketHandler(
                                        "manual-" + credential.name(), perSessionStore, objectMapper, status -> {}),
                                credential.wsUrl())
                        .get(10, TimeUnit.SECONDS);
                sessions.add(session);

                session.sendMessage(new TextMessage(RealtimeSessionManager.registrationMessage(
                        objectMapper, approvalKey, "1",
                        new RealtimeSubscription(RealtimeTickParser.TR_ID, COMPARE_TARGET))));

                Thread.sleep(RECEIVE_WINDOW.toMillis() / 2);

                perSessionStore.findFresh(COMPARE_TARGET)
                        .ifPresent(price -> lastPriceByCredential.put(credential.name(), price));
            }

            assertThat(lastPriceByCredential.keySet())
                    .as("시세를 받은 세션. 모의 세션이 빠졌다면 모의 도메인은 실시간 체결가를 주지 않는다는 뜻이다")
                    .hasSize(sessions.size());

            // 체결은 계속 일어나므로 가격이 완전히 같을 수는 없다. 자리수가 같은지(= 같은 종목의 실제 시세인지)를 본다.
            List<Integer> prices = lastPriceByCredential.values().stream()
                    .map(price -> price.tick().currentPrice())
                    .toList();

            int min = prices.stream().mapToInt(Integer::intValue).min().orElseThrow();
            int max = prices.stream().mapToInt(Integer::intValue).max().orElseThrow();

            assertThat((max - min) / (double) max)
                    .as("세션별 현재가: %s. 차이가 크면 모의 세션이 실제 시세가 아니다", lastPriceByCredential)
                    .isLessThan(0.01);

        } finally {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()){
                    session.close();
                }
            }
        }
    }
}
