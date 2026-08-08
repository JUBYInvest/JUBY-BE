package juby.invest.domain.kis.websocket.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WebsocketErrorCode implements BaseErrorCode {

    // 4xx: 클라이언트/서버 상태 문제
    SESSION_NOT_CONNECTED(HttpStatus.CONFLICT,"WEBSOCKET409_1","KIS 웹소켓이 연결되어 있지 않습니다. 먼저 연결을 수립해주세요."),

    // 502: KIS 서버 문제
    APPROVAL_KEY_ISSUE_FAILED(HttpStatus.BAD_GATEWAY, "WEBSOCKET502_1", "KIS 웹소켓 접속키 발급에 실패했습니다."),
    CONNECTION_FAILED(HttpStatus.BAD_GATEWAY,"WEBSOCKET502_2", "KIS 웹소켓 연결에 실패했습니다."),
    SUBSCRIBE_FAILED(HttpStatus.BAD_GATEWAY,"WEBSOCKET502_3","실시간 체결가 구독 요청에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
