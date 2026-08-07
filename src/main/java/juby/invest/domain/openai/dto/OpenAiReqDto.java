package juby.invest.domain.openai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class    OpenAiReqDto {

    public record AskRequest(

            @NotBlank(message = "질문은 필수입니다.")
            String question,

            @NotBlank(message = "종목명은 필수입니다.")
            String stockName
    ){}
}