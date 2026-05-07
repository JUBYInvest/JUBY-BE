package juby.invest.domain.pinecone.dto;

import lombok.Builder;

import java.time.LocalDateTime;

public class PineconeResDto {

    @Builder
    public record PineconeSuccess(
            LocalDateTime upsertTime
    ){}
}
