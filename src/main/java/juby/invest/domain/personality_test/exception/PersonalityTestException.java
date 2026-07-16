package juby.invest.domain.personality_test.exception;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;

public class PersonalityTestException extends ProjectException {
    public PersonalityTestException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
