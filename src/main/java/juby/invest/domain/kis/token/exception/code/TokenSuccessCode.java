package juby.invest.domain.kis.token.exception.code;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TokenSuccessCode implements BaseSuccessCode {

    MOCK_TOKEN_SUCCESS(HttpStatus.OK, "TOKEN200_1", "mock 토큰이 성공적으로 응답되었습니다."),
    REAL_TOKEN_SUCCESS(HttpStatus.OK, "TOKEN200_2", "real 토큰이 성공적으로 응답되었습니다.")
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
