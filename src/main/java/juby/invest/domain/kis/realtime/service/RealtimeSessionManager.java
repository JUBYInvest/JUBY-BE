package juby.invest.domain.kis.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import juby.invest.domain.kis.realtime.config.KisWebSocketProperties;
import juby.invest.domain.kis.realtime.converter.RealtimeIndexParser;
import juby.invest.domain.kis.realtime.converter.RealtimeTickParser;
import juby.invest.domain.kis.realtime.dto.RealtimeSubscription;
import juby.invest.domain.kis.realtime.exception.RealtimeException;
import juby.invest.domain.kis.realtime.exception.code.RealtimeErrorCode;
import juby.invest.domain.kis.token.service.TokenService;
import juby.invest.domain.marketindex.enums.MarketIndex;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/***
 * KIS 실시간 세션들의 수명을 관리한다. 접속, 구독 등록(종목 체결가 + 시장 지수), 끊김 복구까지.
 * <p>
 * <b>단일 인스턴스 전용이다.</b> appkey 1개당 세션 1개만 허용되므로, 인스턴스가 2대로 늘면
 * 뒤에 붙은 쪽이 거부되거나 앞의 세션이 끊긴다. 스케일아웃하려면 세션 소유권을 한 노드로 모으는
 * 설계가 먼저 필요하다. (현재는 스케줄러에 dev 프로파일을 걸어 EC2 단일 컨테이너에서만 돌린다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeSessionManager {

    private static final String SUBSCRIBE = "1";
    private static final String UNSUBSCRIBE = "2";
    private static final String CUSTOMER_TYPE_PERSONAL = "P";

    private static final long CONNECT_TIMEOUT_SECONDS = 10;

    /*** 접속키 발급은 REST 호출이라 유량 제한이 있다. 세션 수만큼 연달아 발급하므로 사이를 띄운다. */
    private static final long APPROVAL_ISSUE_INTERVAL_MS = 200;

    private static final long INITIAL_BACKOFF_SECONDS = 1;
    private static final long MAX_BACKOFF_SECONDS = 30;

    /*** 1초를 이만큼 시프트하면 이미 상한을 넘는다. 지수를 더 키울 이유가 없다. */
    private static final int MAX_BACKOFF_SHIFT = 6;

    /***
     * 이 횟수만큼 연속 실패하면 ERROR를 1회 남긴다. ERROR는 슬랙 알림 트리거라 매 시도마다 찍으면 알림이 쏟아진다.
     * 안정화되면 카운터가 초기화되므로 장애 1건당 알림 1건이 된다.
     */
    private static final int MAX_FAILURES_BEFORE_ALERT = 5;

    /***
     * 이 시간 이상 버틴 세션의 끊김은 "새로운 장애"로 보고 연속 실패 카운터를 초기화한다.
     * 붙자마자 끊기는 플래핑(중복 접속 거부 등)은 카운터가 계속 쌓여 백오프가 늘고 알림까지 간다.
     */
    private static final Duration MIN_STABLE_SESSION = Duration.ofMinutes(1);

    private final KisWebSocketProperties properties;
    private final TokenService tokenService;
    private final RealtimePriceStore priceStore;
    private final RealtimeIndexStore indexStore;
    private final StockRepository stockRepository;
    private final ObjectMapper objectMapper;
    private final WebSocketClient kisWebSocketClient;
    private final ScheduledExecutorService realtimeReconnectScheduler;

    private final List<SessionHolder> holders = new CopyOnWriteArrayList<>();

    /*** 의도된 종료 중에는 재접속하지 않는다. 재접속 스레드에서도 읽으므로 volatile. */
    private volatile boolean shuttingDown;

    /***
     * 설정된 자격증명마다 세션을 열고 담당 종목을 등록한다.
     */
    public synchronized void connect() {

        if (!holders.isEmpty()){
            log.info("실시간 세션이 이미 떠 있어 연결을 건너뜁니다. 세션 {}개", holders.size());
            return;
        }

        List<KisWebSocketProperties.Credential> credentials = properties.credentials().stream()
                .filter(KisWebSocketProperties.Credential::isConfigured)
                .toList();

        if (credentials.isEmpty()){
            throw new RealtimeException(RealtimeErrorCode.NO_CREDENTIAL_CONFIGURED);
        }

        List<RealtimeSubscription> stockSubscriptions = stockRepository.findAll().stream()
                .map(Stock::getStockCode)
                .map(stockCode -> new RealtimeSubscription(RealtimeTickParser.TR_ID, stockCode))
                .toList();

        // 지수는 실전 세션에 고정한다. 모의 세션이 지수 TR을 지원하지 않으면 거부도 없이 조용히 수신이 끊긴다.
        int realSessionIndex = realDomainSessionIndex(credentials);
        List<RealtimeSubscription> indexSubscriptions = indexSubscriptions(realSessionIndex);

        if (stockSubscriptions.isEmpty() && indexSubscriptions.isEmpty()){
            log.warn("구독할 항목이 없어 실시간 연결을 건너뜁니다.");
            return;
        }

        // 상한을 넘으면 여기서 터진다. 일부 항목이 조용히 누락된 채 운영되는 것보다 낫다.
        List<List<RealtimeSubscription>> allocation = SubscriptionAllocator.allocate(
                stockSubscriptions, indexSubscriptions, Math.max(realSessionIndex, 0), credentials.size());

        shuttingDown = false;
        log.info("실시간 세션 연결 시작. 세션 {}개, 종목 {}건, 지수 {}건",
                credentials.size(), stockSubscriptions.size(), indexSubscriptions.size());

        for (int i = 0; i < credentials.size(); i++) {
            if (i > 0){
                pause(APPROVAL_ISSUE_INTERVAL_MS);
            }
            SessionHolder holder = new SessionHolder(credentials.get(i), allocation.get(i));
            holders.add(holder);
            openSession(holder);
        }
    }

    /*** 실전 도메인 자격증명의 세션 인덱스. 없으면 -1 */
    private int realDomainSessionIndex(List<KisWebSocketProperties.Credential> credentials) {

        for (int i = 0; i < credentials.size(); i++) {
            if (credentials.get(i).realDomain()){
                return i;
            }
        }
        return -1;
    }

    /***
     * 지수 구독 목록. 실전 세션이 없으면 비어 있다(지수는 REST 폴백으로 조회된다).
     */
    private List<RealtimeSubscription> indexSubscriptions(int realSessionIndex) {

        if (realSessionIndex < 0){
            log.warn("실전 자격증명이 없어 지수 실시간 구독을 건너뜁니다. 지수는 REST 폴백으로 조회됩니다.");
            return List.of();
        }

        return Arrays.stream(MarketIndex.values())
                .map(marketIndex -> new RealtimeSubscription(RealtimeIndexParser.TR_ID, marketIndex.getCode()))
                .toList();
    }

    /***
     * 모든 세션의 등록을 해제하고 닫는다. 이후 끊김 알림이 와도 재접속하지 않는다.
     */
    @PreDestroy
    public synchronized void disconnect() {

        if (holders.isEmpty()){
            return;
        }

        shuttingDown = true;
        log.info("실시간 세션 종료 시작. 세션 {}개", holders.size());

        for (SessionHolder holder : holders) {
            try {
                sendRegistration(holder, UNSUBSCRIBE);

                WebSocketSession session = holder.session;
                if (session != null && session.isOpen()){
                    session.close();
                }
            } catch (Exception e) {
                // 종료 경로의 실패는 복구할 것이 없다. 다음 세션 정리를 막지 않는다.
                log.warn("실시간 세션 종료에 실패했습니다. 세션: {}", holder.credential.name(), e);
            }
        }
        holders.clear();
    }

    private void openSession(SessionHolder holder) {

        String sessionName = holder.credential.name();

        try {
            // 접속키는 세션 수립에만 쓰이고 저장하지 않는다. 재접속할 때마다 새로 발급받는다.
            holder.approvalKey = tokenService.issueApprovalKey(
                    holder.credential.restBaseUrl(),
                    holder.credential.appKey(),
                    holder.credential.appSecret());

            WebSocketHandler handler = new RealtimePriceWebSocketHandler(
                    sessionName, priceStore, indexStore, objectMapper, status -> onSessionClosed(holder, status));

            holder.session = kisWebSocketClient.execute(handler, holder.credential.wsUrl())
                    .get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            sendRegistration(holder, SUBSCRIBE);
            holder.connectedAt = Instant.now();

            log.info("실시간 세션 연결 완료. 세션: {}, 담당 구독 {}건", sessionName, holder.subscriptions.size());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("종료 신호를 받아 실시간 세션 연결을 중단합니다. 세션: {}", sessionName);

        } catch (Exception e) {
            registerFailure(holder, "접속 실패", e);
        }
    }

    /***
     * 담당 종목을 등록(tr_type=1) 또는 해제(tr_type=2)한다.
     */
    private void sendRegistration(SessionHolder holder, String trType) throws Exception {

        WebSocketSession session = holder.session;
        if (session == null || !session.isOpen()){
            log.warn("세션이 열려 있지 않아 실시간 등록 요청을 보내지 못했습니다. 세션: {}", holder.credential.name());
            return;
        }

        for (RealtimeSubscription subscription : holder.subscriptions) {
            session.sendMessage(new TextMessage(
                    registrationMessage(objectMapper, holder.approvalKey, trType, subscription)));
        }
    }

    /***
     * 실시간 등록/해제 요청 메시지. 수동 검증 테스트에서도 같은 포맷을 써야 하므로 static으로 열어 둔다.
     *
     * @param trType 1=등록, 2=해제
     */
    static String registrationMessage(ObjectMapper objectMapper, String approvalKey, String trType, RealtimeSubscription subscription) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "header", Map.of(
                        "approval_key", approvalKey,
                        "custtype", CUSTOMER_TYPE_PERSONAL,
                        "tr_type", trType,
                        "content-type", "utf-8"),
                "body", Map.of(
                        "input", Map.of(
                                "tr_id", subscription.trId(),
                                "tr_key", subscription.trKey()))));
    }

    private void onSessionClosed(SessionHolder holder, CloseStatus status) {

        if (shuttingDown){
            log.info("실시간 세션이 정상 종료되었습니다. 세션: {}", holder.credential.name());
            return;
        }

        // 충분히 버틴 세션의 끊김은 직전 장애와 무관한 새 사건이다. 카운터를 초기화하고 다시 센다.
        if (holder.wasStable()){
            holder.consecutiveFailures = 0;
        }
        holder.connectedAt = null;

        registerFailure(holder, "세션 끊김(" + status + ")", null);
    }

    private void registerFailure(SessionHolder holder, String reason, Exception cause) {

        holder.consecutiveFailures++;
        String sessionName = holder.credential.name();

        if (holder.consecutiveFailures == MAX_FAILURES_BEFORE_ALERT){
            log.error("실시간 세션이 {}회 연속 실패했습니다. 세션: {}, 사유: {}",
                    holder.consecutiveFailures, sessionName, reason, cause);
        } else {
            log.warn("실시간 세션 실패({}회). 세션: {}, 사유: {}",
                    holder.consecutiveFailures, sessionName, reason, cause);
        }

        scheduleReconnect(holder);
    }

    private void scheduleReconnect(SessionHolder holder) {

        if (shuttingDown){
            return;
        }

        long delaySeconds = backoffSeconds(holder.consecutiveFailures);
        log.info("실시간 세션 재접속을 {}초 후 시도합니다. 세션: {}", delaySeconds, holder.credential.name());

        // 접속키 재발급도 openSession 안에서 일어나므로, 재접속이 밀리면 발급 호출도 함께 밀린다.
        realtimeReconnectScheduler.schedule(() -> {
            if (!shuttingDown){
                openSession(holder);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /*** 1s -> 2s -> 4s ... 상한 30s */
    static long backoffSeconds(int consecutiveFailures) {

        if (consecutiveFailures <= 1){
            return INITIAL_BACKOFF_SECONDS;
        }

        int shift = Math.min(consecutiveFailures - 1, MAX_BACKOFF_SHIFT);

        return Math.min(INITIAL_BACKOFF_SECONDS << shift, MAX_BACKOFF_SECONDS);
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /*** 세션 1개의 상태. 재접속 스레드와 연결 스레드가 함께 보므로 변경 가능한 필드는 volatile. */
    private static class SessionHolder {

        private final KisWebSocketProperties.Credential credential;
        private final List<RealtimeSubscription> subscriptions;

        private volatile WebSocketSession session;
        private volatile String approvalKey; // 메모리에만 둔다. 로그에 남기지 않는다.
        private volatile Instant connectedAt;
        private volatile int consecutiveFailures;

        private SessionHolder(KisWebSocketProperties.Credential credential, List<RealtimeSubscription> subscriptions) {
            this.credential = credential;
            this.subscriptions = subscriptions;
        }

        private boolean wasStable() {
            return connectedAt != null
                    && Duration.between(connectedAt, Instant.now()).compareTo(MIN_STABLE_SESSION) >= 0;
        }
    }
}
