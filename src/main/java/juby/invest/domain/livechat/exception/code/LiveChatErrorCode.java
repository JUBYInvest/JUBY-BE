package juby.invest.domain.livechat.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LiveChatErrorCode implements BaseErrorCode {

    EMPTY_MESSAGE(HttpStatus.BAD_REQUEST,
            "LIVECHAT400_1",
            "메시지를 입력해주세요."),
    MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST,
            "LIVECHAT400_2",
            "메시지는 300자까지 보낼 수 있습니다."),
    INVALID_MESSAGE(HttpStatus.BAD_REQUEST,
            "LIVECHAT400_3",
            "메시지 형식이 올바르지 않습니다."),
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED,
            "LIVECHAT401_1",
            "로그인 후 채팅에 참여할 수 있습니다."),
    NICKNAME_REQUIRED(HttpStatus.FORBIDDEN,
            "LIVECHAT403_1",
            "닉네임을 설정한 후 채팅에 참여할 수 있습니다."),
    FORBIDDEN_DESTINATION(HttpStatus.FORBIDDEN,
            "LIVECHAT403_2",
            "허용되지 않은 채팅 경로입니다."),
    TOO_MANY_MESSAGES(HttpStatus.TOO_MANY_REQUESTS,
            "LIVECHAT429_1",
            "메시지를 너무 빠르게 보내고 있습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
