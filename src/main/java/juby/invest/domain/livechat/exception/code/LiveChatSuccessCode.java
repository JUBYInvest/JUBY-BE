package juby.invest.domain.livechat.exception.code;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LiveChatSuccessCode implements BaseSuccessCode {

    MESSAGE_LIST_OK(HttpStatus.OK,
            "LIVECHAT200_1",
            "채팅 메시지를 성공적으로 조회하였습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
