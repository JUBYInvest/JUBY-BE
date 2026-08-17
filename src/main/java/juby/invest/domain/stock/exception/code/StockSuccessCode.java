package juby.invest.domain.stock.exception.code;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StockSuccessCode implements BaseSuccessCode {

    STOCK_DETAIL_OK(HttpStatus.OK,"STOCK200_1", "종목 검색 요청이 성공하였습니다."),
    STOCK_NEWS_OK(HttpStatus.OK,"STOCK200_2" ,"종목 뉴스 조회에 성공하였습니다." ),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
