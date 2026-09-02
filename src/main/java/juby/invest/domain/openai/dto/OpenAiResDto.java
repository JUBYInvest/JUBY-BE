package juby.invest.domain.openai.dto;

import lombok.Builder;

@Builder
public class OpenAiResDto {

    @Builder
    public record AskResult(
            String answer, // LLM이 생성한 답변
            Long chatSessionId, // 이번 답변이 저장된 대화방 id (신규 생성된 경우 새로 발급된 id)
            Long messageId // 이번 답변(ASSISTANT 메시지)의 id
    ){}
}