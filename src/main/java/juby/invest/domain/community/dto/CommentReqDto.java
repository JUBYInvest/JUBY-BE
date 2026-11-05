package juby.invest.domain.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CommentReqDto {

    // 작성과 수정이 같은 필드를 받는다.
    public record ContentReq(
            @NotBlank(message = "댓글 내용을 입력해주세요.")
            @Size(max = 500, message = "댓글은 500자 이하로 입력해주세요.")
            String content
    ){}
}
