package juby.invest.domain.stock.dto;

import juby.invest.domain.stock.enums.Order;
import juby.invest.domain.stock.enums.StockSortBy;
import lombok.Builder;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public class StockListDto {

    public record StockListReq(
            StockSortBy sortBy,
            Order order
    ){
        public StockListReq{
            if (sortBy == null) sortBy = StockSortBy.STOCK_NAME;
            if (order == null) order = sortBy.getDefaultOrder();
        }

        // 1차 정렬: sortBy의 order순
        // 2차 정렬: 만약, 1차 조건이 같다면 종목코드 오름차순
        public Comparator<StockList> toComparator(){
            Comparator<StockList> comparator = sortBy.getComparator();

            return (order == Order.DESC ? comparator.reversed() : comparator)
                    .thenComparing(StockList::stockCode);
        }
    }

    public record StockListRes(
            LocalDate baseDate,
            List<StockList> stockList
    ){
        public static StockListRes of(LocalDate baseDate, List<StockList> stockList){
            return new StockListRes(baseDate, stockList);
        }
    }

    @Builder
    public record StockList(
            String stockCode,
            String stockName,
            Integer closePrice,
            Double fluctuate,
            Long tradingValue
    ){
        public static StockList of(String stockCode, String stockName, Integer closePrice, Double comparePrev, Long tradingValue){
            return StockList.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .closePrice(closePrice)
                    .fluctuate(comparePrev)
                    .tradingValue(tradingValue)
                    .build();
        }
    }
}
