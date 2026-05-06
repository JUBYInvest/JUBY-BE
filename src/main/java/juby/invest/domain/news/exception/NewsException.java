package juby.invest.domain.news.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class NewsException extends ProjectException {
    public NewsException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
