package juby.invest.domain.kis.market.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class MarketException extends ProjectException {
    public MarketException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
