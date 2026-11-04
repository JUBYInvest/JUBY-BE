package juby.invest.domain.stock.dto;

import juby.invest.domain.stock.enums.Order;
import juby.invest.domain.stock.enums.StockSortBy;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /***
     * @param baseDate 일봉 기준일. 실시간 값이 섞여 있어도 폴백 기준은 이 날짜다.
     * @param asOf     응답을 만든 시각. 프론트가 "N분 전" 같은 신선도 표시를 할 수 있게 함께 내린다.
     */
    public record StockListRes(
            LocalDate baseDate,
            LocalDateTime asOf,
            List<StockList> stockList
    ){
        public static StockListRes of(LocalDate baseDate, LocalDateTime asOf, List<StockList> stockList){
            return new StockListRes(baseDate, asOf, stockList);
        }
    }

    /***
     * @param closePrice 장중 실시간 값이 있으면 종가 자리에 현재가가 들어간다.
     * @param realtime   이 항목의 가격이 실시간 체결가인지 여부. false면 일봉(종가) 폴백이다.
     */
    @Builder
    public record StockList(
            String stockCode,
            String stockName,
            Integer closePrice,
            Double fluctuate,
            Long tradingValue,
            boolean isLiked,
            boolean realtime
    ){
        // 일봉 기준 항목. 폴백 경로와 기존 호출부가 그대로 쓴다.
        public static StockList of(String stockCode, String stockName, Integer closePrice, Double comparePrev, Long tradingValue, boolean isLiked){
            return of(stockCode, stockName, closePrice, comparePrev, tradingValue, isLiked, false);
        }

        public static StockList of(String stockCode, String stockName, Integer closePrice, Double comparePrev, Long tradingValue, boolean isLiked, boolean realtime){
            return StockList.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .closePrice(closePrice)
                    .fluctuate(comparePrev)
                    .tradingValue(tradingValue)
                    .isLiked(isLiked)
                    .realtime(realtime)
                    .build();
        }
    }
}
