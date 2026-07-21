package juby.invest.domain.personality_test.exception.code;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PersonalityTestSuccessCode implements BaseSuccessCode {

    PERSONALITY_TEST_SUCCESS_CODE(
            HttpStatus.OK,
            "PERSONALITY-TEST200_1",
            "질문 리스트 조회를 성공하였습니다."),
    OK(
            HttpStatus.OK,
            "PERSONALITY-TEST200_2",
            "사용자 성향 테스트 결과가 성공적으로 측정되었습니다.")
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
