package juby.invest.domain.chat.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class ChatException extends ProjectException {
    public ChatException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}