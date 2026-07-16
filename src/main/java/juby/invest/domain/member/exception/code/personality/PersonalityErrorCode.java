package juby.invest.domain.member.exception.code.personality;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PersonalityErrorCode implements BaseErrorCode {

    PERSONALITY_NOT_FOUND(HttpStatus.NOT_FOUND,
            "PERSONALITY404_1",
            "해당 투자성향ID가 존재하지 않습니다. 1 ~ 5 사이의 숫자를 입력해주세요.")
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
