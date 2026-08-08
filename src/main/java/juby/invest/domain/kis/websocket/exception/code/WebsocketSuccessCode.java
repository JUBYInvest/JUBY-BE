package juby.invest.domain.kis.websocket.exception.code;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WebsocketSuccessCode implements BaseSuccessCode {

    APPROVAL_KEY_ISSUED(HttpStatus.OK,"WEBSOCKET200_1" ,"KIS 실시간 접속키 발급에 성공했습니다." ),
    CONNECTED(HttpStatus.OK, "WEBSOCKET200_2", "KIS 웹소켓 커넥션 수립에 성공했습니다."),
    DISCONNECTED(HttpStatus.OK, "WEBSOCKET200_3", "KIS 웹소켓 커넥션 종료에 성공했습니다."),
    SUBSCRIBED(HttpStatus.OK, "WEBSOCKET200_4", "종목 실시간 체결가 구독 등록에 성공했습니다."),
    UNSUBSCRIBED(HttpStatus.OK, "WEBSOCKET200_5", "종목 실시간 체결가 구독 해지에 성공했습니다."),
    ;
    private final HttpStatus status;
    private final String code;
    private final String message;
}
