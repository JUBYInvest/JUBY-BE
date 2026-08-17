package juby.invest.domain.news.exception.code;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum NewsSuccessCode implements BaseSuccessCode {

    NAVER_NEWS_SEARCH_OK(HttpStatus.OK,"NEWS200_1", "네이버 뉴스 검색 API 요청에 성공하였습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
