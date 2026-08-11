package juby.invest.domain.pinecone.dto;

import juby.invest.domain.news.dto.NewsResDto;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public class PineconeDto {

    @Builder
    public record UpsertSuccess(
            NewsResDto.NewsResponse newsResponse,
            LocalDateTime upsertTime
    ){}

    @Builder
    public record BulkUpsertSuccess(
            int totalCount,
            int successCount,
            List<String> failedStocks,
            LocalDateTime upsertTime
    ){}

    @Builder
    public record SearchSuccess(
            List<News> newsList
    ){
        @Builder
        public record News(
                String title,
                String stockName,
                String description,
                String pubDate
        ){}
    }

    @Builder
    public record StockNewsHit(
            String id,
            float score,
            String title,
            String description,
            String originalLink,
            String pubDate
    ){}
}
