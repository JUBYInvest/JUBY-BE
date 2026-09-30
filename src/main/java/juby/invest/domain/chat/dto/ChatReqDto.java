package juby.invest.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class ChatReqDto {

    public record UpdateTitleRequest(

            @NotBlank(message = "제목은 필수입니다.")
            String title
    ) {}
}