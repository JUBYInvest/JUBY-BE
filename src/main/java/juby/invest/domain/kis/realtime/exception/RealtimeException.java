package juby.invest.domain.kis.realtime.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class RealtimeException extends ProjectException {
    public RealtimeException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
