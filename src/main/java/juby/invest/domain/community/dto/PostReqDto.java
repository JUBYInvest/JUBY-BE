package juby.invest.domain.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PostReqDto {

    // 글쓰기 화면 시안의 글자 수 제한(0/40, 0/1000)과 맞춘다.
    public static final int TITLE_MAX = 40;
    public static final int CONTENT_MAX = 1000;

    public record CreateReq(
            // 선택 사항. 보내지 않으면(null) 종목 없는 글이 된다.
            @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다.")
            String stockCode,

            @NotBlank(message = "제목을 입력해주세요.")
            @Size(max = TITLE_MAX, message = "제목은 40자 이하로 입력해주세요.")
            String title,

            @NotBlank(message = "내용을 입력해주세요.")
            @Size(max = CONTENT_MAX, message = "내용은 1000자 이하로 입력해주세요.")
            String content
    ){}

    // 종목은 바꿀 수 없다. 다른 종목 토론방으로 글이 옮겨가면 기존 댓글/좋아요의 맥락이 깨진다.
    public record UpdateReq(
            @NotBlank(message = "제목을 입력해주세요.")
            @Size(max = TITLE_MAX, message = "제목은 40자 이하로 입력해주세요.")
            String title,

            @NotBlank(message = "내용을 입력해주세요.")
            @Size(max = CONTENT_MAX, message = "내용은 1000자 이하로 입력해주세요.")
            String content
    ){}
}
