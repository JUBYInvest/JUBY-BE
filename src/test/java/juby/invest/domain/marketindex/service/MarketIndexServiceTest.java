package juby.invest.domain.marketindex.service;

import juby.invest.domain.kis.market.dto.IndexPriceRes;
import juby.invest.domain.kis.market.exception.MarketException;
import juby.invest.domain.kis.market.exception.code.MarketErrorCode;
import juby.invest.domain.kis.market.service.MarketService;
import juby.invest.domain.kis.realtime.dto.RealtimeIndexTick;
import juby.invest.domain.kis.realtime.service.RealtimeIndexStore;
import juby.invest.domain.marketindex.dto.MarketIndexDto;
import juby.invest.domain.marketindex.enums.MarketIndex;
import juby.invest.domain.marketindex.exception.MarketIndexException;
import juby.invest.domain.marketindex.exception.code.MarketIndexErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("시장 지수 조회")
class MarketIndexServiceTest {

    /*** TTL 경계를 실제 대기 없이 검증하기 위한, 수동으로 흐르는 시계 */
    private static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-03T08:00:00Z"); // 17:00 KST (장외)

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }

        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
    }

    @Mock private MarketService marketService;

    private final MovableClock clock = new MovableClock();
    private RealtimeIndexStore realtimeIndexStore;
    private MarketIndexCache cache;
    private MarketIndexService marketIndexService;

    @BeforeEach
    void setUp() {
        realtimeIndexStore = new RealtimeIndexStore();
        cache = new MarketIndexCache(clock);
        marketIndexService = new MarketIndexService(realtimeIndexStore, marketService, cache);
    }

    private static IndexPriceRes.Info restResponse(String currentIndex, String changeRate) {
        return IndexPriceRes.Info.builder()
                .currentIndex(currentIndex)
                .changeRate(changeRate)
                .accVolume("412345678")
                .accTradingValue("9876543210")
                .build();
    }

    private void putRealtime(String indexCode, String currentIndex) {
        realtimeIndexStore.put(new RealtimeIndexTick(indexCode, "133000",
                new BigDecimal(currentIndex), new BigDecimal("-0.37"),
                new BigDecimal("2550.10"), new BigDecimal("2561.44"), new BigDecimal("2538.07"),
                new BigDecimal("1234"), new BigDecimal("5678")));
    }

    private MarketIndexDto.Item itemOf(MarketIndexDto.MarketIndexRes response, MarketIndex marketIndex) {
        return response.indices().stream()
                .filter(item -> item.code().equals(marketIndex.getCode()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("실시간 값이 있으면 REST를 호출하지 않는다")
    void prefersRealtimeOverRest() throws Exception {

        putRealtime(MarketIndex.KOSPI.getCode(), "2543.21");
        putRealtime(MarketIndex.KOSDAQ.getCode(), "812.45");

        MarketIndexDto.MarketIndexRes response = marketIndexService.getMarketIndices();

        assertThat(response.indices()).hasSize(2);
        MarketIndexDto.Item kospi = itemOf(response, MarketIndex.KOSPI);
        assertThat(kospi.name()).isEqualTo("코스피");
        assertThat(kospi.currentIndex()).isEqualByComparingTo("2543.21");
        assertThat(kospi.changeRate()).isEqualByComparingTo("-0.37");
        assertThat(kospi.realtime()).isTrue();
        assertThat(response.asOf()).isNotNull();

        then(marketService).should(never()).getIndexPrice(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("실시간 값이 없으면 KIS 현재지수로 폴백한다")
    void fallsBackToRest() throws Exception {

        given(marketService.getIndexPrice(MarketIndex.KOSPI.getCode())).willReturn(restResponse("2500.11", "0.42"));
        given(marketService.getIndexPrice(MarketIndex.KOSDAQ.getCode())).willReturn(restResponse("800.22", "-1.10"));

        MarketIndexDto.MarketIndexRes response = marketIndexService.getMarketIndices();

        MarketIndexDto.Item kospi = itemOf(response, MarketIndex.KOSPI);
        assertThat(kospi.currentIndex()).isEqualByComparingTo("2500.11");
        assertThat(kospi.changeRate()).isEqualByComparingTo("0.42");
        assertThat(kospi.accTradingValue()).isEqualByComparingTo("9876543210");
        assertThat(kospi.realtime()).isFalse();
    }

    @Test
    @DisplayName("실시간 값이 일부만 있으면 그 지수만 실시간으로 응답한다")
    void mixesRealtimeAndRest() throws Exception {

        putRealtime(MarketIndex.KOSPI.getCode(), "2543.21");
        given(marketService.getIndexPrice(MarketIndex.KOSDAQ.getCode())).willReturn(restResponse("800.22", "-1.10"));

        MarketIndexDto.MarketIndexRes response = marketIndexService.getMarketIndices();

        assertThat(itemOf(response, MarketIndex.KOSPI).realtime()).isTrue();
        assertThat(itemOf(response, MarketIndex.KOSDAQ).realtime()).isFalse();
        then(marketService).should(never()).getIndexPrice(MarketIndex.KOSPI.getCode());
    }

    @Test
    @DisplayName("캐시가 살아 있는 동안에는 REST를 다시 호출하지 않는다")
    void reusesCachedRestResponse() throws Exception {

        given(marketService.getIndexPrice(MarketIndex.KOSPI.getCode())).willReturn(restResponse("2500.11", "0.42"));
        given(marketService.getIndexPrice(MarketIndex.KOSDAQ.getCode())).willReturn(restResponse("800.22", "-1.10"));

        marketIndexService.getMarketIndices();
        clock.advanceSeconds(59);
        marketIndexService.getMarketIndices();

        // 장외에는 값이 변하지 않으므로 요청마다 KIS를 호출할 이유가 없다.
        then(marketService).should(times(1)).getIndexPrice(MarketIndex.KOSPI.getCode());
        then(marketService).should(times(1)).getIndexPrice(MarketIndex.KOSDAQ.getCode());
    }

    @Test
    @DisplayName("캐시 기간이 지나면 REST를 다시 호출한다")
    void refetchesAfterCacheExpiry() throws Exception {

        given(marketService.getIndexPrice(MarketIndex.KOSPI.getCode())).willReturn(restResponse("2500.11", "0.42"));
        given(marketService.getIndexPrice(MarketIndex.KOSDAQ.getCode())).willReturn(restResponse("800.22", "-1.10"));

        marketIndexService.getMarketIndices();
        clock.advanceSeconds(61);
        marketIndexService.getMarketIndices();

        then(marketService).should(times(2)).getIndexPrice(MarketIndex.KOSPI.getCode());
    }

    @Test
    @DisplayName("지수 1건이 실패해도 나머지는 응답한다")
    void returnsRemainingIndicesWhenOneFails() throws Exception {

        given(marketService.getIndexPrice(MarketIndex.KOSPI.getCode())).willReturn(restResponse("2500.11", "0.42"));
        willThrow(new MarketException(MarketErrorCode.INDEX_PRICE_FAILED))
                .given(marketService).getIndexPrice(MarketIndex.KOSDAQ.getCode());

        MarketIndexDto.MarketIndexRes response = marketIndexService.getMarketIndices();

        // 코스닥 조회가 막혔다고 코스피까지 화면에서 사라질 이유는 없다.
        assertThat(response.indices()).hasSize(1);
        assertThat(response.indices().getFirst().code()).isEqualTo(MarketIndex.KOSPI.getCode());
    }

    @Test
    @DisplayName("지수를 하나도 구하지 못하면 예외를 던진다")
    void throwsWhenEveryIndexFails() throws Exception {

        willThrow(new MarketException(MarketErrorCode.INDEX_PRICE_FAILED))
                .given(marketService).getIndexPrice(org.mockito.ArgumentMatchers.anyString());

        assertThatThrownBy(() -> marketIndexService.getMarketIndices())
                .isInstanceOf(MarketIndexException.class)
                .hasFieldOrPropertyWithValue("errorCode", MarketIndexErrorCode.INDEX_FETCH_FAILED);
    }

    @Test
    @DisplayName("현재 지수 값이 깨진 지수는 응답에서 제외한다")
    void excludesIndexWithUnparsableValue() throws Exception {

        given(marketService.getIndexPrice(MarketIndex.KOSPI.getCode())).willReturn(restResponse("", "0.42"));
        given(marketService.getIndexPrice(MarketIndex.KOSDAQ.getCode())).willReturn(restResponse("800.22", "-1.10"));

        MarketIndexDto.MarketIndexRes response = marketIndexService.getMarketIndices();

        assertThat(response.indices()).hasSize(1);
        assertThat(response.indices().getFirst().code()).isEqualTo(MarketIndex.KOSDAQ.getCode());
    }

    @Test
    @DisplayName("등락률이나 거래대금이 비어 있어도 지수는 응답한다")
    void keepsIndexWhenOptionalFieldsAreBlank() throws Exception {

        given(marketService.getIndexPrice(MarketIndex.KOSPI.getCode())).willReturn(
                IndexPriceRes.Info.builder().currentIndex("2500.11").changeRate("").accTradingValue(null).build());
        given(marketService.getIndexPrice(MarketIndex.KOSDAQ.getCode())).willReturn(restResponse("800.22", "-1.10"));

        MarketIndexDto.Item kospi = itemOf(marketIndexService.getMarketIndices(), MarketIndex.KOSPI);

        assertThat(kospi.currentIndex()).isEqualByComparingTo("2500.11");
        assertThat(kospi.changeRate()).isNull();
        assertThat(kospi.accTradingValue()).isNull();
    }
}
