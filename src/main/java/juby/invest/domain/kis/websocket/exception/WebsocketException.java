package juby.invest.domain.kis.websocket.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class WebsocketException extends ProjectException {
    public WebsocketException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
