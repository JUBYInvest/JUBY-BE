package juby.invest.domain.stock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import juby.invest.domain.news.enums.NewsSortType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public record StockNewsDto() {

    // 종목 상세 페이지 뉴스 요청
    public record StockNewsReq(
            NewsSortType sort, // LATEST or RELEVANCE

            @Min(value = 0, message = "page는 최소 0입니다.")
            @Max(value = 9, message = "page는 최대 9입니다.")
            Integer page
    ){
        public StockNewsReq{
            if (sort == null) sort = NewsSortType.LATEST;
            if (page == null) page = 0;
        }
    }

    // 종목 상세 페이지 뉴스 응답
    @Builder
    public record StockNewsRes(
            String stockCode,
            String stockName,
            NewsSortType sort,
            List<NewsItem> newsList,
            int page,
            int totalCount
    ){
        public static StockNewsRes of(
                String stockCode,
                String stockName,
                NewsSortType sort,
                List<NewsItem> newsList,
                int page, int totalCount
        ){
            return StockNewsRes.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .sort(sort)
                    .newsList(newsList)
                    .page(page)
                    .totalCount(totalCount)
                    .build();
        }
    }
    @Builder
    public record NewsItem(
            String timeAgo,
            LocalDateTime publishedAt,
            String title,
            String description,
            String originalLink
    ){}
}
