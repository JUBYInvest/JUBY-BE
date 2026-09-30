package juby.invest.domain.openai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class    OpenAiReqDto {

    public record AskRequest(

            @NotBlank(message = "질문은 필수입니다.")
            @Size(max = 1000, message = "질문은 1000자 이하로 입력해주세요.")
            String question,

            // 특정 종목 페이지에서 호출하는 경우에만 채워서 보내면 됨. 비어있으면 질문 내용에서 종목명을 찾아냄.
            String stockName,

            // 이어갈 대화방 id. 없으면(null) 새 대화방을 생성해서 시작한다.
            Long chatSessionId
    ){}
}