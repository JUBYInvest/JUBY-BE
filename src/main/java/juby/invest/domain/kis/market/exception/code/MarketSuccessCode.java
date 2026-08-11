package juby.invest.domain.kis.market.exception.code;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum MarketSuccessCode implements BaseSuccessCode {


    CURRENT_PRICE_OK(HttpStatus.OK,"MARKET200_1","실시간 현재가 조회에 성공하였습니다."),
    TRADE_VOLUME_OK(HttpStatus.OK,"MARKET200_2","TOP 30 거래량 조회에 성공하였습니다."),
    DAILY_PRICE_OK(HttpStatus.OK,"MARKET200_3","주식 현재가 일자별 조회에 성공하였습니다."),
    PERIOD_DAILY_PRICE_OK(HttpStatus.OK,"MARKET200_4","주식 기간별 시세 조회에 성공하였습니다."),
    HOLIDAY_OK(HttpStatus.OK,"MARKET200_5","휴장일 조회에 성공하였습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
