package juby.invest.domain.kis.token.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class TokenException extends ProjectException {
  public TokenException(BaseErrorCode errorCode) {
    super(errorCode);
  }
}
