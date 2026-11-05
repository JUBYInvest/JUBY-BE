package juby.invest.domain.stock.dto;

import juby.invest.domain.stock.enums.Period;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

public class StockDetailDto {

    /***
     * @param realtime 현재가가 실시간 체결가인지 여부. false면 KIS 현재가 REST 응답이다.
     */
    @Builder
    public record StockDetailRes(
            String stockName,
            String stockCode,
            int currentPrice,
            double comparePrev,
            Period period,
            List<DailyPrices> dailyPrices,
            boolean realtime
    ){
        public static StockDetailRes of(String stockName, String stockCode, int currentPrice, Double comparePrev, Period period, List<DailyPrices> dailyPrices, boolean realtime){
            return StockDetailRes.builder()
                    .stockName(stockName)
                    .stockCode(stockCode)
                    .currentPrice(currentPrice)
                    .comparePrev(comparePrev)
                    .period(period)
                    .dailyPrices(dailyPrices)
                    .realtime(realtime)
                    .build();
        }
    }

    @Builder
    public record DailyPrices(
            LocalDate date,
            int openPrice,
            int highPrice,
            int lowPrice,
            int closePrice,
            int volume
    ){}
}
