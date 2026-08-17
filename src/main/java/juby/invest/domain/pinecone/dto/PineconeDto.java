package juby.invest.domain.pinecone.dto;

import juby.invest.domain.news.dto.NewsDto;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public class PineconeDto {

    // Pinecone에 뉴스 레코드를 등록했을 때의 반환 DTO
    @Builder
    public record UpsertSuccess(
            NewsDto.NaverNewsRes naverNewsResponse,
            LocalDateTime upsertTime
    ){}

    @Builder
    public record BulkUpsertSuccess(
            int totalCount,
            int successCount,
            List<String> failedStocks,
            LocalDateTime upsertTime
    ){}

    // Pinecone에서 Semantic Search를 통해 찾은 뉴스 레코드 DTO
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
