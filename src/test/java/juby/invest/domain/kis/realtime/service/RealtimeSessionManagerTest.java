package juby.invest.domain.kis.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import juby.invest.domain.kis.realtime.config.KisWebSocketProperties;
import juby.invest.domain.kis.realtime.exception.RealtimeException;
import juby.invest.domain.kis.realtime.exception.code.RealtimeErrorCode;
import juby.invest.domain.kis.token.service.TokenService;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("실시간 세션 매니저")
class RealtimeSessionManagerTest {

    @Mock private TokenService tokenService;
    @Mock private StockRepository stockRepository;
    @Mock private WebSocketClient webSocketClient;
    @Mock private ScheduledExecutorService reconnectScheduler;

    /*** 매니저가 세션별로 만든 핸들러. 끊김을 흉내내기 위해 붙잡아 둔다. */
    private WebSocketHandler capturedHandler;

    private static KisWebSocketProperties.Credential credential(String name) {
        return new KisWebSocketProperties.Credential(
                name, "appkey-" + name, "secret-" + name,
                "https://openapi.koreainvestment.com:9443", "ws://ops.koreainvestment.com:21000");
    }

    private static Stock stock(String stockCode) {
        return Stock.builder().stockCode(stockCode).stockName(stockCode + "종목").build();
    }

    private RealtimeSessionManager manager(List<KisWebSocketProperties.Credential> credentials) {
        return new RealtimeSessionManager(
                new KisWebSocketProperties(credentials),
                tokenService, new RealtimePriceStore(), stockRepository,
                new ObjectMapper(), webSocketClient, reconnectScheduler);
    }

    /*** 접속하면 이 세션을 돌려주고, 매니저가 만든 핸들러를 붙잡는다. */
    private WebSocketSession givenConnectableSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        given(session.isOpen()).willReturn(true);
        given(webSocketClient.execute(any(WebSocketHandler.class), anyString()))
                .willAnswer(invocation -> {
                    capturedHandler = invocation.getArgument(0);
                    return CompletableFuture.completedFuture(session);
                });
        return session;
    }

    /*** 재접속 예약을 즉시 실행한다. 테스트에서 백오프 시간을 기다리지 않기 위한 장치. */
    private void givenImmediateReconnect() {
        given(reconnectScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .willAnswer(invocation -> {
                    invocation.getArgument(0, Runnable.class).run();
                    return null;
                });
    }

    @Test
    @DisplayName("자격증명마다 세션을 열고 담당 종목을 등록한다")
    void opensSessionPerCredentialAndSubscribes() throws Exception {

        WebSocketSession session = givenConnectableSession();
        given(tokenService.issueApprovalKey(anyString(), anyString(), anyString())).willReturn("approval");
        given(stockRepository.findAll()).willReturn(List.of(
                stock("005930"), stock("000660"), stock("035720"), stock("005380"), stock("068270")));

        manager(List.of(credential("real"), credential("mock-1"))).connect();

        // 세션 2개 접속 + 접속키 세션당 1회 발급
        then(webSocketClient).should(times(2)).execute(any(WebSocketHandler.class), anyString());
        then(tokenService).should(times(2)).issueApprovalKey(anyString(), anyString(), anyString());
        // 종목 5개가 세션 2개에 3/2로 나뉘어 총 5건 등록
        then(session).should(times(5)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("등록 메시지에 체결가 TR과 등록 구분을 담는다")
    void sendsSubscribeMessageWithTrId() throws Exception {

        WebSocketSession session = givenConnectableSession();
        given(tokenService.issueApprovalKey(anyString(), anyString(), anyString())).willReturn("approval");
        given(stockRepository.findAll()).willReturn(List.of(stock("005930")));

        manager(List.of(credential("real"))).connect();

        org.mockito.ArgumentCaptor<TextMessage> captor = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        then(session).should().sendMessage(captor.capture());

        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("H0STCNT0");
        assertThat(payload).contains("005930");
        assertThat(payload).contains("approval");
        assertThat(payload).contains("\"tr_type\":\"1\"");
    }

    @Test
    @DisplayName("예기치 않게 끊기면 접속키를 다시 발급받아 재접속하고 종목을 재등록한다")
    void reconnectsAndResubscribesAfterUnexpectedClose() throws Exception {

        WebSocketSession session = givenConnectableSession();
        given(tokenService.issueApprovalKey(anyString(), anyString(), anyString())).willReturn("approval");
        given(stockRepository.findAll()).willReturn(List.of(stock("005930"), stock("000660")));
        givenImmediateReconnect();

        RealtimeSessionManager manager = manager(List.of(credential("real")));
        manager.connect();

        capturedHandler.afterConnectionClosed(session, CloseStatus.SERVER_ERROR);

        // 최초 1회 + 재접속 1회
        then(tokenService).should(times(2)).issueApprovalKey(anyString(), anyString(), anyString());
        then(webSocketClient).should(times(2)).execute(any(WebSocketHandler.class), anyString());
        // 종목 2개 등록이 두 번 (최초 + 재등록)
        then(session).should(times(4)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("접속에 실패하면 재접속을 예약한다")
    void schedulesReconnectOnConnectFailure() {

        given(tokenService.issueApprovalKey(anyString(), anyString(), anyString())).willReturn("approval");
        given(stockRepository.findAll()).willReturn(List.of(stock("005930")));
        given(webSocketClient.execute(any(WebSocketHandler.class), anyString()))
                .willReturn(CompletableFuture.failedFuture(new IllegalStateException("접속 거부")));

        manager(List.of(credential("real"))).connect();

        then(reconnectScheduler).should().schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("의도한 종료 뒤에는 끊김 알림이 와도 재접속하지 않는다")
    void doesNotReconnectAfterIntentionalDisconnect() throws Exception {

        WebSocketSession session = givenConnectableSession();
        given(tokenService.issueApprovalKey(anyString(), anyString(), anyString())).willReturn("approval");
        given(stockRepository.findAll()).willReturn(List.of(stock("005930")));

        RealtimeSessionManager manager = manager(List.of(credential("real")));
        manager.connect();
        manager.disconnect();

        capturedHandler.afterConnectionClosed(session, CloseStatus.NORMAL);

        then(reconnectScheduler).should(never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        then(tokenService).should(times(1)).issueApprovalKey(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("종료할 때 등록을 해제하고 세션을 닫는다")
    void unsubscribesAndClosesOnDisconnect() throws Exception {

        WebSocketSession session = givenConnectableSession();
        given(tokenService.issueApprovalKey(anyString(), anyString(), anyString())).willReturn("approval");
        given(stockRepository.findAll()).willReturn(List.of(stock("005930")));

        RealtimeSessionManager manager = manager(List.of(credential("real")));
        manager.connect();

        org.mockito.ArgumentCaptor<TextMessage> captor = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        manager.disconnect();

        then(session).should(atLeastOnce()).sendMessage(captor.capture());
        assertThat(captor.getAllValues()).last()
                .extracting(TextMessage::getPayload, org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("\"tr_type\":\"2\"");
        then(session).should().close();
    }

    @Test
    @DisplayName("이미 연결돼 있으면 중복으로 세션을 열지 않는다")
    void skipsConnectWhenAlreadyConnected() {

        givenConnectableSession();
        given(tokenService.issueApprovalKey(anyString(), anyString(), anyString())).willReturn("approval");
        given(stockRepository.findAll()).willReturn(List.of(stock("005930")));

        RealtimeSessionManager manager = manager(List.of(credential("real")));
        manager.connect();
        manager.connect();

        then(webSocketClient).should(times(1)).execute(any(WebSocketHandler.class), anyString());
    }

    @Test
    @DisplayName("자격증명이 하나도 설정되지 않으면 연결을 거부한다")
    void rejectsWhenNoCredentialConfigured() {

        KisWebSocketProperties.Credential blank = new KisWebSocketProperties.Credential(
                "mock-2", "", "", "https://openapivts.koreainvestment.com:29443", "ws://ops.koreainvestment.com:31000");

        assertThatThrownBy(() -> manager(List.of(blank)).connect())
                .isInstanceOf(RealtimeException.class)
                .hasFieldOrPropertyWithValue("errorCode", RealtimeErrorCode.NO_CREDENTIAL_CONFIGURED);
    }

    @Test
    @DisplayName("종목 수가 세션 등록 상한을 넘으면 접속 전에 실패한다")
    void failsBeforeConnectingWhenStocksExceedCapacity() {

        List<Stock> tooManyStocks = java.util.stream.IntStream
                .range(0, SubscriptionAllocator.MAX_SUBSCRIPTION_PER_SESSION + 1)
                .mapToObj(i -> stock(String.format("%06d", i)))
                .toList();
        given(stockRepository.findAll()).willReturn(tooManyStocks);

        assertThatThrownBy(() -> manager(List.of(credential("real"))).connect())
                .isInstanceOf(RealtimeException.class)
                .hasFieldOrPropertyWithValue("errorCode", RealtimeErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED);

        // 접속키 발급도, 접속도 시도하지 않는다.
        then(tokenService).should(never()).issueApprovalKey(anyString(), anyString(), anyString());
        then(webSocketClient).should(never()).execute(any(WebSocketHandler.class), anyString());
    }

    @Test
    @DisplayName("재접속 간격은 1초에서 시작해 30초를 넘지 않는다")
    void backsOffExponentiallyUpToCap() {

        assertThat(RealtimeSessionManager.backoffSeconds(1)).isEqualTo(1);
        assertThat(RealtimeSessionManager.backoffSeconds(2)).isEqualTo(2);
        assertThat(RealtimeSessionManager.backoffSeconds(3)).isEqualTo(4);
        assertThat(RealtimeSessionManager.backoffSeconds(6)).isEqualTo(30);
        assertThat(RealtimeSessionManager.backoffSeconds(50)).isEqualTo(30);
    }
}
