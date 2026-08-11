package juby.invest.domain.stock.converter;

import juby.invest.domain.stock.dto.StockDetailDto;
import juby.invest.domain.stock.entity.DailyPrice;

public class StockConverter {

    public static StockDetailDto.DailyPrices toDailyPrices(DailyPrice dailyPrice){
        return StockDetailDto.DailyPrices.builder()
                .date(dailyPrice.getDate())
                .openPrice(dailyPrice.getOpenPrice())
                .highPrice(dailyPrice.getHighPrice())
                .lowPrice(dailyPrice.getLowPrice())
                .closePrice(dailyPrice.getClosePrice())
                .volume(dailyPrice.getVolume())
                .build();
    }
}
