package juby.invest.domain.stock.dto;

import juby.invest.domain.stock.enums.Period;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

public class StockDetailDto {

    @Builder
    public record StockDetailRes(
            String stockName,
            String stockCode,
            int currentPrice,
            double comparePrev,
            Period period,
            List<DailyPrices> dailyPrices
    ){
        public static StockDetailRes of(String stockName, String stockCode, int currentPrice, Double comparePrev, Period period, List<DailyPrices> dailyPrices){
            return StockDetailRes.builder()
                    .stockName(stockName)
                    .stockCode(stockCode)
                    .currentPrice(currentPrice)
                    .comparePrev(comparePrev)
                    .period(period)
                    .dailyPrices(dailyPrices)
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
