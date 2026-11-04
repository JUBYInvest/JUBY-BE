package juby.invest.domain.kis.realtime.service;

import jakarta.annotation.PostConstruct;
import juby.invest.domain.kis.realtime.dto.IndexEvent;
import juby.invest.domain.kis.realtime.dto.PriceEvent;
import juby.invest.domain.kis.realtime.dto.RealtimeIndex;
import juby.invest.domain.kis.realtime.dto.RealtimePrice;
import juby.invest.domain.marketindex.enums.MarketIndex;
import juby.invest.domain.stock.exception.StockException;
import juby.invest.domain.stock.exception.code.StockErrorCode;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/***
 * 실시간 시세를 브라우저로 밀어 보낸다.
 * <p>
 * 틱마다 보내지 않는다. 100종목 장중 체결은 초당 수백 건이라 그대로 밀면 브라우저가 이벤트 처리에 묶인다.
 * 1초마다 <b>직전 전송 이후 값이 바뀐 종목만</b> 모아 보낸다.
 * <p>
 * 변경 판정은 연결별 상태 없이 <b>전역 시각 하나</b>로 한다. 연결마다 마지막 스냅샷을 들고 비교하면
 * 연결 수 × 종목 수만큼 메모리를 쓰지만, 저장소가 이미 수신 시각을 들고 있으므로
 * "그 시각 이후에 들어온 것"만 고르면 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeStreamService {

    /*** 전 종목 변경분 이벤트 이름 */
    static final String PRICES_EVENT = "prices";
    /*** 지수 변경분 이벤트 이름 */
    static final String INDICES_EVENT = "indices";
    /*** 종목 1건 이벤트 이름 (상세 화면 연결) */
    static final String PRICE_EVENT = "price";

    private static final Duration BROADCAST_INTERVAL = Duration.ofSeconds(1);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    /***
     * 연결 수명. 브라우저 EventSource는 끊기면 스스로 재연결하므로, 무한히 붙들기보다
     * 주기적으로 끊어 좀비 연결을 정리하는 편이 낫다.
     */
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

    private final RealtimePriceStore priceStore;
    private final RealtimeIndexStore indexStore;
    private final RealtimeStreamRegistry registry;
    private final StockRepository stockRepository;
    private final ScheduledExecutorService realtimeStreamScheduler;

    /***
     * 직전 브로드캐스트 시각. 스케줄러 스레드가 쓰고 읽는다.
     * EPOCH는 "아직 보낸 적 없음"이다. 기동 직후 저장소는 비어 있으므로 첫 주기에 쏟아질 값도 없다.
     */
    private volatile Instant lastBroadcastAt = Instant.EPOCH;

    @PostConstruct
    void startBroadcasting() {

        realtimeStreamScheduler.scheduleAtFixedRate(this::broadcastSafely,
                BROADCAST_INTERVAL.toMillis(), BROADCAST_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);

        realtimeStreamScheduler.scheduleAtFixedRate(this::heartbeatSafely,
                HEARTBEAT_INTERVAL.toMillis(), HEARTBEAT_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    /***
     * 목록 화면 연결을 연다. 연결 직후 현재 스냅샷을 한 번 내려 화면을 채운다.
     */
    public SseEmitter openPriceStream() {

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        registry.register(emitter);

        sendSnapshot(emitter);
        return emitter;
    }

    /***
     * 상세 화면 연결을 연다.
     *
     * @param stockCode 구독할 종목코드
     */
    public SseEmitter openStockStream(String stockCode) {

        // 없는 종목이면 영원히 아무것도 오지 않는 연결이 열린다. 조용히 두는 것보다 즉시 알리는 편이 낫다.
        if (!stockRepository.existsById(stockCode)){
            throw new StockException(StockErrorCode.STOCK_NOT_FOUND);
        }

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        registry.register(stockCode, emitter);

        priceStore.findFresh(stockCode)
                .ifPresent(price -> registry.sendTo(emitter, PRICE_EVENT, PriceEvent.from(price.tick())));

        return emitter;
    }

    /*** 새 연결에 현재 신선한 값 전체를 한 번 보낸다. */
    private void sendSnapshot(SseEmitter emitter) {

        List<PriceEvent> prices = priceStore.findAllFresh().values().stream()
                .map(price -> PriceEvent.from(price.tick()))
                .toList();

        if (!prices.isEmpty() && !registry.sendTo(emitter, PRICES_EVENT, prices)){
            return; // 첫 전송부터 실패하면 이미 끊긴 연결이다
        }

        List<IndexEvent> indices = currentIndices(Instant.EPOCH);
        if (!indices.isEmpty()){
            registry.sendTo(emitter, INDICES_EVENT, indices);
        }
    }

    /***
     * 1초 주기 브로드캐스트.
     * <p>
     * <b>예외를 밖으로 내보내면 안 된다.</b> scheduleAtFixedRate는 작업이 예외를 던지면 이후 실행을
     * 조용히 중단한다. 그러면 로그 한 줄 없이 푸시가 영영 멈춘다.
     */
    private void broadcastSafely() {
        try {
            broadcast();
        } catch (Exception e) {
            log.warn("실시간 스트림 브로드캐스트에 실패했습니다.", e);
        }
    }

    private void heartbeatSafely() {
        try {
            registry.heartbeat();
        } catch (Exception e) {
            log.warn("실시간 스트림 하트비트에 실패했습니다.", e);
        }
    }

    void broadcast() {

        // 기준 시각을 먼저 잡는다. 수집과 갱신 사이에 들어온 틱은 다음 주기에 잡히므로 유실되지 않는다.
        Instant now = Instant.now();
        Instant changedAfter = lastBroadcastAt;
        lastBroadcastAt = now;

        if (registry.connectionCount() == 0){
            return;
        }

        List<PriceEvent> changedPrices = changedPrices(changedAfter);
        if (!changedPrices.isEmpty()){
            registry.broadcast(PRICES_EVENT, changedPrices);

            // 상세 화면 연결은 자기 종목만 받는다.
            changedPrices.forEach(price -> registry.sendToStock(price.stockCode(), PRICE_EVENT, price));
        }

        List<IndexEvent> changedIndices = currentIndices(changedAfter);
        if (!changedIndices.isEmpty()){
            registry.broadcast(INDICES_EVENT, changedIndices);
        }
    }

    private List<PriceEvent> changedPrices(Instant changedAfter) {

        Map<String, RealtimePrice> fresh = priceStore.findAllFresh();

        return fresh.values().stream()
                .filter(price -> price.receivedAt().isAfter(changedAfter))
                .map(price -> PriceEvent.from(price.tick()))
                .toList();
    }

    /*** 기준 시각 이후에 갱신된 지수. 기준을 EPOCH로 주면 현재 신선한 지수 전체가 된다. */
    private List<IndexEvent> currentIndices(Instant changedAfter) {

        List<IndexEvent> events = new ArrayList<>();

        for (MarketIndex marketIndex : Arrays.asList(MarketIndex.values())) {
            Optional<RealtimeIndex> index = indexStore.findFresh(marketIndex.getCode())
                    .filter(value -> value.receivedAt().isAfter(changedAfter));

            index.ifPresent(value -> events.add(IndexEvent.from(marketIndex, value.tick())));
        }
        return events;
    }
}
