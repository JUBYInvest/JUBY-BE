package juby.invest.domain.kis.token.exception.code;

import com.google.api.Http;
import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TokenErrorCode implements BaseErrorCode {

    TOKEN_IS_NULL(HttpStatus.BAD_REQUEST, "TOKEN400_1", "암호화할 토큰이 없습니다."),
    TOKEN_ENCRYPT_FAILED(HttpStatus.BAD_REQUEST, "TOKEN400_2", "토큰 암호화에 실패했습니다."),
    TOKEN_DECRYPT_FAILED(HttpStatus.BAD_REQUEST, "TOKEN400_3", "토큰 복호화에 실패했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
