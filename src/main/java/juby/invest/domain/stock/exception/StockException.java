package juby.invest.domain.stock.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class StockException extends ProjectException {
  public StockException(BaseErrorCode errorCode) {
    super(errorCode);
  }
}
