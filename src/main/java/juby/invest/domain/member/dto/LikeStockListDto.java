package juby.invest.domain.member.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class LikeStockListDto {

    @Builder
    public record LikeStockListRes(
            LocalDate baseDate,
            Integer totalCount,
            List<LikeStockList> likeStockList
    ){
        public static LikeStockListRes of(LocalDate baseDate, Integer totalCount, List<LikeStockList> likeStockList){
            return LikeStockListRes.builder()
                    .baseDate(baseDate)
                    .totalCount(totalCount)
                    .likeStockList(likeStockList)
                    .build();
        }
    }

    /***
     * @param closePrice 장중 실시간 값이 있으면 종가 자리에 현재가가 들어간다.
     * @param realtime   이 항목의 가격이 실시간 체결가인지 여부. false면 일봉(종가) 폴백이다.
     */
    @Builder
    public record LikeStockList(
            String stockCode,
            String stockName,
            Integer closePrice,
            Double fluctuate,
            Long tradingValue,
            LocalDateTime likedAt,
            boolean realtime
    ){
        public static LikeStockList of(
                String stockCode,
                String stockName,
                Integer closePrice,
                Double fluctuate,
                Long tradingValue,
                LocalDateTime likedAt,
                boolean realtime
        ){
            return LikeStockList.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .closePrice(closePrice)
                    .fluctuate(fluctuate)
                    .tradingValue(tradingValue)
                    .likedAt(likedAt)
                    .realtime(realtime)
                    .build();
        }
    }
}
