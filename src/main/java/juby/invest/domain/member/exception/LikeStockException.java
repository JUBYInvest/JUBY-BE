package juby.invest.domain.member.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class LikeStockException extends ProjectException {
    public LikeStockException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
