package juby.invest.domain.openai.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class OpenAiException extends ProjectException {
    public OpenAiException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}