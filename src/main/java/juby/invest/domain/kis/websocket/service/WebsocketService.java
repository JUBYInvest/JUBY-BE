package juby.invest.domain.kis.websocket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juby.invest.domain.kis.websocket.dto.ApprovalKeyDto;
import juby.invest.domain.kis.websocket.dto.RealtimeSubscribeDto;
import juby.invest.domain.kis.websocket.enums.TrType;
import juby.invest.domain.kis.websocket.exception.WebsocketException;
import juby.invest.domain.kis.websocket.exception.code.WebsocketErrorCode;
import juby.invest.domain.kis.websocket.handler.KisRealtimePriceHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebsocketService {

    private static final long CONNECT_TIMEOUT_SECONDS = 5L;

    private final RestClient mockInvestRestClient;
    private final KisRealtimePriceHandler kisRealtimePriceHandler;
    private final ObjectMapper objectMapper;
    private final StandardWebSocketClient webSocketClient = new StandardWebSocketClient();

    @Value("${kis.mock.app-key}") private String appKey;
    @Value("${kis.mock.app-secret}") private String appSecret;
    @Value("${kis.mock.socket-domain}") private String socketDomain;

    private WebSocketSession session;
    private String approvalKey;

    /***
     * 함수 기능: KIS 실시간 (웹소켓) 접속키를 발급받는다.
     * @return ApprovalKeyRes 웹소켓 접속키
     */
    public ApprovalKeyDto.ApprovalKeyRes getApprovalKey() {

        ApprovalKeyDto.ApprovalKeyReq requestBody = ApprovalKeyDto.ApprovalKeyReq.of(appKey, appSecret);

        try {
            return mockInvestRestClient.post()
                    .uri("/oauth2/Approval")
                    .header("content-type", "application/json;utf-8")
                    .body(requestBody)
                    .retrieve()
                    .body(ApprovalKeyDto.ApprovalKeyRes.class);
        } catch (RestClientException e) {
            log.error("[WebsocketService] KIS 웹소켓 접속키 발급 실패", e);
            throw new WebsocketException(WebsocketErrorCode.APPROVAL_KEY_ISSUE_FAILED);
        }
    }

    /***
     * 함수 기능: KIS 실시간 웹소켓에 연결한다. 접속키 발급 -> 커넥션 수립까지 수행한다.
     */
    public void connect(){

        // 접속키 발급
        this.approvalKey = getApprovalKey().approvalKey();

        try {
            this.session = webSocketClient.execute(kisRealtimePriceHandler, socketDomain)
                    .get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e){
            log.error("[WebsocketService] KIS 웹소켓 연결 실패", e);
            throw new WebsocketException(WebsocketErrorCode.CONNECTION_FAILED);
        }
    }

    /***
     * 함수 기능: KIS 실시간 웹소켓 연결을 종료한다.
     */
    public void disconnect(){
        if (session == null || !session.isOpen()){
            return;
        }

        try {
            session.close(CloseStatus.NORMAL);
        } catch (IOException e) {
            log.error("[WebsocketService] KIS 웹소켓 종료 실패", e);
        } finally {
            this.session = null;
            this.approvalKey = null;
        }
    }

    /***
     * 함수 기능: 종목의 실시간 체결가 구독을 등록한다.
     * @param stockCode 종목코드 6자리
     */
    public void subscribeRealtimePrice(String stockCode){
        sendSubscribeMessage(stockCode, TrType.REGISTER);
    }

    /***
     * 함수 기능: 종목의 실시간 체결가 구독을 해지한다.
     * @param stockCode 종목코드 6자리
     */
    public void unsubscribeRealtimePrice(String stockCode){
        sendSubscribeMessage(stockCode, TrType.UNREGISTER);
    }

    /***
     * 함수 기능: KIS 실시간 체결가 구독/해지를 요청한다.
     * @param stockCode 종목코드 6자리
     * @param trType 거래타입 1: 등록 2: 해제
     */
    private void sendSubscribeMessage(String stockCode, TrType trType) {
        if (session == null || !session.isOpen())
            throw new WebsocketException(WebsocketErrorCode.SESSION_NOT_CONNECTED);

        RealtimeSubscribeDto.RealtimeSubscribeReq request = RealtimeSubscribeDto.RealtimeSubscribeReq.of(approvalKey, trType.getCode(), stockCode);

        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(request)));
        } catch (JsonProcessingException e) {
            log.error("[WebsocketService] 구독 요청 직렬화 실패: {}", stockCode, e);
            throw new WebsocketException(WebsocketErrorCode.SUBSCRIBE_FAILED);
        } catch (Exception e) {
            log.error("[WebsocketService] 구독 요청 전송 실패: {}", stockCode, e);
            throw new WebsocketException(WebsocketErrorCode.SUBSCRIBE_FAILED);
        }
    }
}
