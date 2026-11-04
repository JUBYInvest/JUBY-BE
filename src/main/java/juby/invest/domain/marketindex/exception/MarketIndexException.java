package juby.invest.domain.marketindex.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class MarketIndexException extends ProjectException {
    public MarketIndexException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
