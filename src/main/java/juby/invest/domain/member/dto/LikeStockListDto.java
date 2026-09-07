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

    @Builder
    public record LikeStockList(
            String stockCode,
            String stockName,
            Integer closePrice,
            Double fluctuate,
            Long tradingValue,
            LocalDateTime likedAt
    ){
        public static LikeStockList of(
                String stockCode,
                String stockName,
                Integer closePrice,
                Double fluctuate,
                Long tradingValue,
                LocalDateTime likedAt
        ){
            return LikeStockList.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .closePrice(closePrice)
                    .fluctuate(fluctuate)
                    .tradingValue(tradingValue)
                    .likedAt(likedAt)
                    .build();
        }
    }
}
