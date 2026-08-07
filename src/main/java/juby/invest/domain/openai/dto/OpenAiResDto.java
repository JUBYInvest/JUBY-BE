package juby.invest.domain.openai.dto;

import lombok.Builder;

@Builder
public class OpenAiResDto {

    @Builder
    public record AskResult(
            String answer // LLM이 생성한 답변
    ){}
}