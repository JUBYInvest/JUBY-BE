package juby.invest.domain.openai.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OpenAiErrorCode implements BaseErrorCode {

    EMPTY_ANSWER(HttpStatus.BAD_GATEWAY,
            "OPENAI502_1",
            "AI 답변 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}