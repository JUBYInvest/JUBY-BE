package juby.invest.domain.news.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NewsErrorCode implements BaseErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "NEWS404_1", "네이버 뉴스 API 요청을 실패하였습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "NEWS429_1", "네이버 뉴스 API 호출 제한을 초과하여 재시도에 실패하였습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
