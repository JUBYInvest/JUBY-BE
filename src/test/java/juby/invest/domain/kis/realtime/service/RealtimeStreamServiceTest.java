package juby.invest.domain.kis.realtime.service;

import juby.invest.domain.kis.realtime.dto.IndexEvent;
import juby.invest.domain.kis.realtime.dto.PriceEvent;
import juby.invest.domain.kis.realtime.dto.RealtimeIndexTick;
import juby.invest.domain.kis.realtime.dto.RealtimeTick;
import juby.invest.domain.marketindex.enums.MarketIndex;
import juby.invest.domain.stock.exception.StockException;
import juby.invest.domain.stock.exception.code.StockErrorCode;
import juby.invest.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("실시간 스트림 서비스")
class RealtimeStreamServiceTest {

    @Mock private RealtimeStreamRegistry registry;
    @Mock private StockRepository stockRepository;
    @Mock private ScheduledExecutorService scheduler;

    private RealtimePriceStore priceStore;
    private RealtimeIndexStore indexStore;
    private RealtimeStreamService streamService;

    @BeforeEach
    void setUp() {
        priceStore = new RealtimePriceStore();
        indexStore = new RealtimeIndexStore();
        streamService = new RealtimeStreamService(
                priceStore, indexStore, registry, stockRepository, scheduler);

        // 연결이 하나도 없으면 브로드캐스트를 건너뛰므로, 기본적으로 연결이 있는 상태로 둔다.
        given(registry.connectionCount()).willReturn(1);
        given(registry.sendTo(any(SseEmitter.class), anyString(), any())).willReturn(true);
    }

    private void putTick(String stockCode, int currentPrice) {
        priceStore.put(new RealtimeTick(stockCode, "133000", currentPrice, 1.25,
                70000, 74000, 69000, 1234L, 5678L));
    }

    private void putIndexTick(String indexCode, String currentIndex) {
        indexStore.put(new RealtimeIndexTick(indexCode, "133000",
                new BigDecimal(currentIndex), new BigDecimal("-0.37"),
                new BigDecimal("2550.10"), new BigDecimal("2561.44"), new BigDecimal("2538.07"),
                new BigDecimal("1234"), new BigDecimal("5678")));
    }

    @SuppressWarnings("unchecked")
    private List<PriceEvent> capturedPrices() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        then(registry).should().broadcast(eq("prices"), captor.capture());
        return (List<PriceEvent>) captor.getValue();
    }

    @Test
    @DisplayName("직전 전송 이후 들어온 종목만 보낸다")
    void broadcastsOnlyChangedPrices() {

        putTick("005930", 73100);
        putTick("035720", 41500);
        streamService.broadcast();

        assertThat(capturedPrices()).extracting(PriceEvent::stockCode)
                .containsExactlyInAnyOrder("005930", "035720");

        // 두 번째 주기: 카카오만 새로 들어왔다.
        org.mockito.Mockito.clearInvocations(registry);
        given(registry.connectionCount()).willReturn(1);
        putTick("035720", 41900);

        streamService.broadcast();

        List<PriceEvent> second = capturedPrices();
        assertThat(second).hasSize(1);
        assertThat(second.getFirst().stockCode()).isEqualTo("035720");
        assertThat(second.getFirst().currentPrice()).isEqualTo(41900);
    }

    @Test
    @DisplayName("변경된 값이 없으면 아무것도 보내지 않는다")
    void doesNotBroadcastWithoutChanges() {

        putTick("005930", 73100);
        streamService.broadcast();

        org.mockito.Mockito.clearInvocations(registry);
        given(registry.connectionCount()).willReturn(1);

        streamService.broadcast();

        then(registry).should(never()).broadcast(anyString(), any());
    }

    @Test
    @DisplayName("연결이 없으면 브로드캐스트를 건너뛴다")
    void skipsBroadcastWithoutConnections() {

        given(registry.connectionCount()).willReturn(0);
        putTick("005930", 73100);

        streamService.broadcast();

        then(registry).should(never()).broadcast(anyString(), any());
    }

    @Test
    @DisplayName("지수 변경분은 indices 이벤트로 보낸다")
    void broadcastsChangedIndices() {

        putIndexTick(MarketIndex.KOSPI.getCode(), "2543.21");

        streamService.broadcast();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        then(registry).should().broadcast(eq("indices"), captor.capture());

        @SuppressWarnings("unchecked")
        List<IndexEvent> indices = (List<IndexEvent>) captor.getValue();
        assertThat(indices).hasSize(1);
        assertThat(indices.getFirst().code()).isEqualTo(MarketIndex.KOSPI.getCode());
        assertThat(indices.getFirst().name()).isEqualTo("코스피");
        assertThat(indices.getFirst().currentIndex()).isEqualByComparingTo("2543.21");
    }

    @Test
    @DisplayName("변경된 종목은 해당 종목 연결에도 따로 보낸다")
    void routesChangedPriceToStockStream() {

        putTick("005930", 73100);

        streamService.broadcast();

        then(registry).should().sendToStock(eq("005930"), eq("price"), any(PriceEvent.class));
    }

    @Test
    @DisplayName("목록 연결을 열면 등록하고 현재 스냅샷을 한 번 보낸다")
    void opensPriceStreamWithSnapshot() {

        putTick("005930", 73100);
        putIndexTick(MarketIndex.KOSPI.getCode(), "2543.21");

        SseEmitter emitter = streamService.openPriceStream();

        assertThat(emitter).isNotNull();
        then(registry).should().register(emitter);
        then(registry).should().sendTo(eq(emitter), eq("prices"), any());
        then(registry).should().sendTo(eq(emitter), eq("indices"), any());
    }

    @Test
    @DisplayName("보낼 값이 없으면 스냅샷을 보내지 않는다")
    void opensPriceStreamWithoutSnapshotWhenEmpty() {

        SseEmitter emitter = streamService.openPriceStream();

        then(registry).should().register(emitter);
        then(registry).should(never()).sendTo(any(SseEmitter.class), anyString(), any());
    }

    @Test
    @DisplayName("종목 연결을 열면 그 종목으로 등록하고 현재가를 한 번 보낸다")
    void opensStockStreamWithCurrentPrice() {

        given(stockRepository.existsById("005930")).willReturn(true);
        putTick("005930", 73100);

        SseEmitter emitter = streamService.openStockStream("005930");

        then(registry).should().register(eq("005930"), eq(emitter));
        then(registry).should().sendTo(eq(emitter), eq("price"), any(PriceEvent.class));
    }

    @Test
    @DisplayName("없는 종목으로 연결을 열면 거부한다")
    void rejectsUnknownStock() {

        given(stockRepository.existsById("999999")).willReturn(false);

        // 조용히 열어두면 영원히 아무것도 오지 않는 연결이 된다.
        assertThatThrownBy(() -> streamService.openStockStream("999999"))
                .isInstanceOf(StockException.class)
                .hasFieldOrPropertyWithValue("errorCode", StockErrorCode.STOCK_NOT_FOUND);

        then(registry).should(never()).register(anyString(), any(SseEmitter.class));
    }

    @Test
    @DisplayName("기동 시 브로드캐스트와 하트비트를 예약한다")
    void schedulesBroadcastAndHeartbeat() {

        streamService.startBroadcasting();

        then(scheduler).should(org.mockito.Mockito.times(2))
                .scheduleAtFixedRate(any(Runnable.class), org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(), any(java.util.concurrent.TimeUnit.class));
    }
}
