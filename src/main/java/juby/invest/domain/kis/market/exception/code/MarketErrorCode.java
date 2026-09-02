package juby.invest.domain.kis.market.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum MarketErrorCode implements BaseErrorCode {

    DAILY_PRICE_FAILED(HttpStatus.BAD_GATEWAY,"MARKET502_1","현재가 시세 API 호출 에러가 발생하였습니다."),
    PERIOD_STOCK_PRICE_FAILED(HttpStatus.BAD_GATEWAY,"MARKET502_2","국내 주식 기간별 시세 API 호출 에러가 발생하였습니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;
}
