package juby.invest.domain.pinecone.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class PineconeException extends ProjectException {
    public PineconeException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
