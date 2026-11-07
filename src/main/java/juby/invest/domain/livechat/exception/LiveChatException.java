package juby.invest.domain.livechat.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class LiveChatException extends ProjectException {
    public LiveChatException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
