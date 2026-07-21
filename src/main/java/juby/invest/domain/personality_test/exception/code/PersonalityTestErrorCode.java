package juby.invest.domain.personality_test.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PersonalityTestErrorCode implements BaseErrorCode {

    SCORE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "PERSONALITY-TEST404_1",
            "산출된 점수에 해당되는 투자 성향이 존재하지 않습니다."),
    PERSONALITY_NOT_FOUND(HttpStatus.NOT_FOUND,
            "PERSONALITY-TEST404_2",
            "해당되는 투자 성향이 존재하지 않습니다.")
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
