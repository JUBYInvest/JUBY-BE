package juby.invest.domain.member.converter;

import juby.invest.domain.member.dto.LikeStockListDto;
import juby.invest.domain.member.entity.LikeStock;
import juby.invest.domain.stock.converter.StockConverter;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;

import java.util.Map;

public class LikeStockConverter {

    // 관심 종목 1건을 응답 항목으로 변환한다.
    public static LikeStockListDto.LikeStockList convertToLikeStockList(LikeStock likeStock, Map<String, DailyPrice> basePrices, Map<String, DailyPrice> prevPrices) {

        Stock stock = likeStock.getStock();
        DailyPrice basePrice = basePrices.get(likeStock.getStock().getStockCode());
        DailyPrice prevPrice = prevPrices.get(likeStock.getStock().getStockCode());

        return LikeStockListDto.LikeStockList.of(
                    stock.getStockCode(),
                    stock.getStockName(),
                    basePrice.getClosePrice(),
                    StockConverter.calculateFluctuation(basePrice.getClosePrice(), prevPrice.getClosePrice()),
                    basePrice.getTradingValue(),
                    likeStock.getLikedAt());
    }
}
