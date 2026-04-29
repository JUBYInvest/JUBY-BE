package juby.invest.domain.backtest.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class BacktestException extends ProjectException {
    public BacktestException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
