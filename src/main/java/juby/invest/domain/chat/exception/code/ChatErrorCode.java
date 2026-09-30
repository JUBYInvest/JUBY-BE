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
            "본인의 대화방만 접근할 수 있습니다."),
    ANSWER_IN_PROGRESS(HttpStatus.CONFLICT,
            "CHAT409_1",
            "이전 질문에 대한 답변을 생성 중입니다. 답변이 끝난 뒤 다시 질문해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}