package juby.invest.domain.pinecone.dto;

import juby.invest.domain.news.dto.NewsResDto;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public class PineconeResDto {

    @Builder
    public record UpsertSuccess(

            NewsResDto.NewsResponse newsResponse,
            LocalDateTime upsertTime
    ){}
}
