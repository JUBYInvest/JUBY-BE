package juby.invest.domain.stock.service;

import juby.invest.domain.backtest.repository.BacktestPresetResultRepository;
import juby.invest.domain.kis.market.dto.CurrentPriceRes;
import juby.invest.domain.kis.market.service.MarketService;
import juby.invest.domain.kis.realtime.dto.RealtimeTick;
import juby.invest.domain.kis.realtime.service.RealtimePriceStore;
import juby.invest.domain.member.repository.LikeStockRepository;
import juby.invest.domain.pinecone.service.PineconeService;
import juby.invest.domain.stock.converter.StockConverter;
import juby.invest.domain.stock.dto.StockDetailDto;
import juby.invest.domain.stock.dto.StockListDto;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.enums.Period;
import juby.invest.domain.stock.enums.StockSortBy;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockService 등락률 계산")
class StockServiceTest {

    @DisplayName("전일 종가 대비 등락률을 소수 둘째 자리까지 반환한다")
    @ParameterizedTest(name = "종가 {0}원, 전일 종가 {1}원 -> {2}%")
    @CsvSource({
            //  종가,  전일종가,  기대 등락률
            " 70800,  70300,    0.71",   // 상승 (+0.711237...%)
            " 69000,  70000,   -1.43",   // 하락 (-1.428571...%)
            " 70000,  70000,    0.00",   // 보합
            " 13000,  10000,   30.00",   // 상한가
            "  7000,  10000,  -30.00",   // 하한가
            " 20000,  10000,  100.00",   // 100% 상승 (증자/액면분할 등 예외 상황)
            "    50,  10000,  -99.50",   // 극단적 하락
    })
    void calculateFluctuation(int closePrice, int prevClosePrice, double expected){

        double actual = StockConverter.calculateFluctuation(closePrice, prevClosePrice);

        assertThat(actual).isEqualTo(expected);
    }

    @Nested
    @DisplayName("전일 종가가 없는 경우")
    class WhenPrevCloseIsUnavailable {

        @Test
        @DisplayName("전일 종가가 null이면 0.0을 반환한다")
        void returnZeroWhenPrevCloseIsNull(){
            assertThat(StockConverter.calculateFluctuation(70000, null)).isZero();
        }

        @Test
        @DisplayName("전일 종가가 0이면 0으로 나누지 않고, 0.0을 반환한다")
        void returnZeroWhenPrevCloseIsZero(){
            assertThat(StockConverter.calculateFluctuation(80000, 0)).isZero();
        }
    }

    @Test
    @DisplayName("결과는 항상 소수 둘째 자리 이하이다")
    void alwaysHasAtMostTwoDecimals(){
        for (int closePrice = 7000; closePrice <= 13000; closePrice++){
            double actual = StockConverter.calculateFluctuation(closePrice, 10000);

            assertThat(BigDecimal.valueOf(actual).scale())
                    .as("종가 %d원의 등락률 %s", closePrice, actual)
                    .isLessThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("실시간 시세 반영")
    class RealtimePriceFallback {

        @Mock private StockRepository stockRepository;
        @Mock private DailyPriceRepository dailyPriceRepository;
        @Mock private LikeStockRepository likeStockRepository;
        @Mock private MarketService marketService;
        @Mock private PineconeService pineconeService;
        @Mock private BacktestPresetResultRepository backtestPresetResultRepository;

        private final RealtimePriceStore store = new RealtimePriceStore();

        private static final LocalDate BASE_DATE = LocalDate.of(2026, 11, 3);
        private static final LocalDate PREV_DATE = LocalDate.of(2026, 10, 30);

        private final Stock samsung = Stock.builder().stockCode("005930").stockName("삼성전자").build();
        private final Stock kakao = Stock.builder().stockCode("035720").stockName("카카오").build();

        private StockService stockService() {
            return new StockService(stockRepository, dailyPriceRepository, likeStockRepository,
                    marketService, store, pineconeService, backtestPresetResultRepository);
        }

        private DailyPrice dailyPrice(Stock stock, LocalDate date, int closePrice) {
            return DailyPrice.builder()
                    .stock(stock).date(date)
                    .openPrice(closePrice).highPrice(closePrice).lowPrice(closePrice).closePrice(closePrice)
                    .volume(1000).tradingValue(1_000_000L)
                    .build();
        }

        private void putTick(String stockCode, int currentPrice, double changeRate, long accTradingValue) {
            store.put(new RealtimeTick(stockCode, "133000", currentPrice, changeRate,
                    1000, 2000, 900, 10L, accTradingValue));
        }

        @Test
        @DisplayName("실시간 값이 있는 종목만 실시간으로, 나머지는 일봉으로 응답한다")
        void mixesRealtimeAndDailyPrice() {

            given(dailyPriceRepository.findMaxDate()).willReturn(BASE_DATE);
            given(dailyPriceRepository.findMaxDateBefore(BASE_DATE)).willReturn(PREV_DATE);
            given(dailyPriceRepository.findAllByDateWithStock(BASE_DATE)).willReturn(List.of(
                    dailyPrice(samsung, BASE_DATE, 70800), dailyPrice(kakao, BASE_DATE, 41500)));
            given(dailyPriceRepository.findAllByDateWithStock(PREV_DATE)).willReturn(List.of(
                    dailyPrice(samsung, PREV_DATE, 70300), dailyPrice(kakao, PREV_DATE, 41000)));

            // 삼성전자만 장중 체결이 들어온 상태
            putTick("005930", 73100, -0.14, 3_000_000_000_000L);

            StockListDto.StockListRes response = stockService()
                    .getStockList(null, new StockListDto.StockListReq(null, null));

            StockListDto.StockList realtimeItem = response.stockList().stream()
                    .filter(item -> item.stockCode().equals("005930")).findFirst().orElseThrow();
            StockListDto.StockList fallbackItem = response.stockList().stream()
                    .filter(item -> item.stockCode().equals("035720")).findFirst().orElseThrow();

            assertThat(realtimeItem.realtime()).isTrue();
            assertThat(realtimeItem.closePrice()).isEqualTo(73100);
            assertThat(realtimeItem.fluctuate()).isEqualTo(-0.14);
            assertThat(realtimeItem.tradingValue()).isEqualTo(3_000_000_000_000L);

            assertThat(fallbackItem.realtime()).isFalse();
            assertThat(fallbackItem.closePrice()).isEqualTo(41500);
            assertThat(fallbackItem.fluctuate()).isEqualTo(1.22); // (41500 - 41000) / 41000

            assertThat(response.baseDate()).isEqualTo(BASE_DATE);
            assertThat(response.asOf()).isNotNull();
        }

        @Test
        @DisplayName("정렬은 실시간 값을 기준으로 이뤄진다")
        void sortsByRealtimeValue() {

            given(dailyPriceRepository.findMaxDate()).willReturn(BASE_DATE);
            given(dailyPriceRepository.findMaxDateBefore(BASE_DATE)).willReturn(PREV_DATE);
            given(dailyPriceRepository.findAllByDateWithStock(BASE_DATE)).willReturn(List.of(
                    dailyPrice(samsung, BASE_DATE, 70800), dailyPrice(kakao, BASE_DATE, 41500)));
            given(dailyPriceRepository.findAllByDateWithStock(PREV_DATE)).willReturn(List.of());

            // 일봉 종가로는 삼성전자(70800) > 카카오(41500)지만, 실시간으로는 역전된다.
            putTick("005930", 10_000, 0.0, 1L);
            putTick("035720", 90_000, 0.0, 1L);

            StockListDto.StockListRes response = stockService()
                    .getStockList(null, new StockListDto.StockListReq(StockSortBy.CLOSE_PRICE, null));

            assertThat(response.stockList()).extracting(StockListDto.StockList::stockCode)
                    .containsExactly("005930", "035720");
        }

        @Test
        @DisplayName("상세 조회에서 실시간 값이 있으면 KIS 현재가 API를 호출하지 않는다")
        void skipsRestCallWhenRealtimeHits() throws Exception {

            given(stockRepository.findByStockCode("005930")).willReturn(Optional.of(samsung));
            given(dailyPriceRepository.findMaxDateByStock(samsung)).willReturn(BASE_DATE);
            given(dailyPriceRepository.findAllByStockAndDateGreaterThanEqualOrderByDateAsc(any(), any()))
                    .willReturn(List.of(dailyPrice(samsung, BASE_DATE, 70800)));

            putTick("005930", 73100, -0.14, 1L);

            StockDetailDto.StockDetailRes response = stockService().getStockDetails("005930", Period.ONE_MONTH);

            assertThat(response.currentPrice()).isEqualTo(73100);
            assertThat(response.comparePrev()).isEqualTo(-0.14);
            assertThat(response.realtime()).isTrue();
            then(marketService).should(never()).getDailyPrice(anyString());
        }

        @Test
        @DisplayName("상세 조회에서 실시간 값이 없으면 KIS 현재가 API로 폴백한다")
        void fallsBackToRestCallWhenRealtimeMisses() throws Exception {

            given(stockRepository.findByStockCode("005930")).willReturn(Optional.of(samsung));
            given(dailyPriceRepository.findMaxDateByStock(samsung)).willReturn(BASE_DATE);
            given(dailyPriceRepository.findAllByStockAndDateGreaterThanEqualOrderByDateAsc(any(), any()))
                    .willReturn(List.of(dailyPrice(samsung, BASE_DATE, 70800)));
            given(marketService.getDailyPrice("005930")).willReturn(CurrentPriceRes.Info.builder()
                    .currentPrice("70800").dayChange("0.71").build());

            StockDetailDto.StockDetailRes response = stockService().getStockDetails("005930", Period.ONE_MONTH);

            assertThat(response.currentPrice()).isEqualTo(70800);
            assertThat(response.comparePrev()).isEqualTo(0.71);
            assertThat(response.realtime()).isFalse();
        }
    }
}
