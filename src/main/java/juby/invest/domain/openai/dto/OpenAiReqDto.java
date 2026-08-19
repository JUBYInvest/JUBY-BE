package juby.invest.domain.openai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class    OpenAiReqDto {

    public record AskRequest(

            @NotBlank(message = "질문은 필수입니다.")
            String question,

            // 특정 종목 페이지에서 호출하는 경우에만 채워서 보내면 됨. 비어있으면 질문 내용에서 종목명을 찾아냄.
            String stockName
    ){}
}