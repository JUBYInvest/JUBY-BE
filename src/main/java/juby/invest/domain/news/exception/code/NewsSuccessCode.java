package juby.invest.domain.news.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum NewsSuccessCode implements BaseSuccessCode {

    OK(HttpStatus.OK, "네이버 뉴스 검색 API 요청에 성공했습니다.", "NEWS200_1");

    private final HttpStatus status;
    private final String message;
    private final String code;
}
