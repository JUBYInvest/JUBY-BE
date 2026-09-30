package juby.invest.domain.auth.exception.code;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {

    REISSUE_OK(HttpStatus.OK,"AUTH200_1" ,"AT, RT 재발급에 성공하였습니다." ),
    LOGOUT_OK(HttpStatus.OK,"AUTH200_2" ,"로그아웃에 성공하였습니다." );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
