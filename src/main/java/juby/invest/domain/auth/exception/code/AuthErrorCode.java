package juby.invest.domain.auth.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED,"AUTH401_1","만료된 토큰입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED,"AUTH401_2" ,"유효하지 않은 토큰입니다." ),
    UNKNOWN_TOKEN_ERROR(HttpStatus.UNAUTHORIZED,"AUTH401_3" ,"토큰 검증 중 오류가 발생했습니다." );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
