package juby.invest.domain.member.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class PersonalityException extends ProjectException {
    public PersonalityException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
