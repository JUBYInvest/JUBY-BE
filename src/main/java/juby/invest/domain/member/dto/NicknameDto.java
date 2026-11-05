package juby.invest.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

import java.time.LocalDateTime;

public class NicknameDto {

    // 중복 확인과 설정이 같은 규칙을 쓰도록 한 곳에 둔다.
    public static final String REGEX = "^[가-힣a-zA-Z0-9]{2,10}$";
    public static final String MESSAGE = "닉네임은 한글, 영문, 숫자로 2~10자여야 합니다.";

    public record NicknameReq(
            @NotBlank(message = "닉네임을 입력해주세요.")
            @Pattern(regexp = REGEX, message = MESSAGE)
            String nickname
    ){}

    @Builder
    public record NicknameRes(
            String nickname,
            LocalDateTime modifiedAt
    ){}

    @Builder
    public record CheckRes(
            String nickname,
            boolean available
    ){}
}
