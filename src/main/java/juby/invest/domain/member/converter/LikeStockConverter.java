package juby.invest.domain.member.converter;

import juby.invest.domain.member.dto.LikeStockListDto;
import juby.invest.domain.member.entity.LikeStock;
import juby.invest.domain.stock.converter.StockConverter;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;

import java.util.Map;

public class LikeStockConverter {

    // 관심 종목 1건을 응답 항목으로 변환한다.
    // 기준일에 해당 종목의 일봉이 없으면(신규 상장 등) 가격 관련 필드는 null로 응답한다.
    public static LikeStockListDto.LikeStockList convertToLikeStockList(LikeStock likeStock, Map<String, DailyPrice> basePrices, Map<String, DailyPrice> prevPrices) {

        Stock stock = likeStock.getStock();
        DailyPrice basePrice = basePrices.get(stock.getStockCode());
        DailyPrice prevPrice = prevPrices.get(stock.getStockCode());

        Integer closePrice = (basePrice == null) ? null : basePrice.getClosePrice();
        Integer prevClosePrice = (prevPrice == null) ? null : prevPrice.getClosePrice();
        Long tradingValue = (basePrice == null) ? null : basePrice.getTradingValue();

        // 기준일 가격 자체가 없으면 등락률도 계산할 수 없다.
        Double fluctuate = (closePrice == null) ? null : StockConverter.calculateFluctuation(closePrice, prevClosePrice);

        return LikeStockListDto.LikeStockList.of(
                    stock.getStockCode(),
                    stock.getStockName(),
                    closePrice,
                    fluctuate,
                    tradingValue,
                    likeStock.getLikedAt());
    }
}
