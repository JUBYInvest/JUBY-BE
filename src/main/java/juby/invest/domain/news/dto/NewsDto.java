package juby.invest.domain.news.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import juby.invest.domain.news.enums.NewsSortType;
import juby.invest.domain.pinecone.dto.PineconeDto;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public class NewsDto {

    // 네이버 뉴스 API 호출 응답
    @Builder
    public record NaverNewsRes(
          @JsonProperty("display")
          Integer display,

          @JsonProperty("items")
          List<ItemDetail> itemList
    ){}

    @Builder
    public record ItemDetail(
            String title,
            String originallink,
            String description,
            String pubDate
    ){}
}
