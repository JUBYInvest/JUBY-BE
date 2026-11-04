package juby.invest.domain.stock.converter;

import juby.invest.domain.kis.realtime.dto.RealtimePrice;
import juby.invest.domain.kis.realtime.dto.RealtimeTick;
import juby.invest.domain.stock.dto.StockListDto;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("종목 목록 항목 변환")
class StockConverterTest {

    private final Stock stock = Stock.builder().stockCode("005930").stockName("삼성전자").build();

    private final DailyPrice dailyPrice = DailyPrice.builder()
            .stock(stock)
            .date(LocalDate.of(2026, 11, 3))
            .openPrice(70000).highPrice(71000).lowPrice(69500).closePrice(70800)
            .volume(1_000_000)
            .tradingValue(2_500_000_000_000L)
            .build();

    private static RealtimePrice realtimePrice(int currentPrice, double changeRate, long accTradingValue) {
        return new RealtimePrice(
                new RealtimeTick("005930", "133000", currentPrice, changeRate,
                        70000, 74000, 69000, 1234L, accTradingValue),
                Instant.parse("2026-11-03T04:30:00Z"));
    }

    @Test
    @DisplayName("실시간 시세가 있으면 현재가/전일대비율/누적거래대금을 쓴다")
    void prefersRealtimePrice() {

        StockListDto.StockList result = StockConverter.toStockList(
                dailyPrice, 70300, true, realtimePrice(73100, -0.14, 3_000_000_000_000L));

        assertThat(result.closePrice()).isEqualTo(73100);
        assertThat(result.fluctuate()).isEqualTo(-0.14);
        assertThat(result.tradingValue()).isEqualTo(3_000_000_000_000L);
        assertThat(result.realtime()).isTrue();
        assertThat(result.isLiked()).isTrue();
    }

    @Test
    @DisplayName("실시간 시세가 없으면 일봉 종가로 내려가고 등락률을 직접 계산한다")
    void fallsBackToDailyPrice() {

        StockListDto.StockList result = StockConverter.toStockList(dailyPrice, 70300, false, null);

        assertThat(result.closePrice()).isEqualTo(70800);
        assertThat(result.fluctuate()).isEqualTo(0.71); // (70800 - 70300) / 70300
        assertThat(result.tradingValue()).isEqualTo(2_500_000_000_000L);
        assertThat(result.realtime()).isFalse();
    }

    @Test
    @DisplayName("폴백 경로에서 전일 종가가 없으면 등락률은 0.0이다")
    void fallsBackWithoutPrevClose() {

        StockListDto.StockList result = StockConverter.toStockList(dailyPrice, null, false, null);

        assertThat(result.fluctuate()).isEqualTo(0.0);
        assertThat(result.realtime()).isFalse();
    }

    @Test
    @DisplayName("폴백 경로에서 거래대금이 비어 있으면 0으로 응답한다")
    void fallsBackWithNullTradingValue() {

        DailyPrice withoutTradingValue = DailyPrice.builder()
                .stock(stock).date(LocalDate.of(2026, 11, 3))
                .openPrice(70000).highPrice(71000).lowPrice(69500).closePrice(70800).volume(1)
                .build();

        StockListDto.StockList result = StockConverter.toStockList(withoutTradingValue, 70300, false, null);

        assertThat(result.tradingValue()).isZero();
    }
}
