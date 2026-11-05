package juby.invest.domain.community.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class CommunityException extends ProjectException {
    public CommunityException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
