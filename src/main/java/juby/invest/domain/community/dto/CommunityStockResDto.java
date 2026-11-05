package juby.invest.domain.community.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public class CommunityStockResDto {

    // 커뮤니티 상단 종목 카드 줄. 맨 앞 [전체] 카드는 totalPostCount로 그린다.
    @Builder
    public record StockBoard(
            long totalPostCount, // 종목 없는 글 포함 전체 글 수
            List<StockCard> stocks,
            LocalDateTime priceAsOf // 카드 등락률의 기준 시각. 시세를 하나도 못 가져왔으면 null
    ){}

    @Builder
    public record StockCard(
            String stockCode,
            String stockName,
            long postCount,
            Double changeRate // 전일 대비 등락률(%). 시세 조회에 실패하면 null
    ){}
}
