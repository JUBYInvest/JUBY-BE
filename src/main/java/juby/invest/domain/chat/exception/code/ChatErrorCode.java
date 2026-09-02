package juby.invest.domain.chat.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {

    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND,
            "CHAT404_1",
            "대화방을 찾을 수 없습니다."),
    SESSION_FORBIDDEN(HttpStatus.FORBIDDEN,
            "CHAT403_1",
            "본인의 대화방만 접근할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}