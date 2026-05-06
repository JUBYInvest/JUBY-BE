package juby.invest.domain.news.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

public class NewsResDto {

    @Builder
    public record NewsResponse(
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
