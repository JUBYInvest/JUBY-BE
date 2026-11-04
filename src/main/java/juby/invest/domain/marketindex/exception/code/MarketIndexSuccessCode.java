package juby.invest.domain.marketindex.exception.code;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MarketIndexSuccessCode implements BaseSuccessCode {

    MARKET_INDEX_OK(HttpStatus.OK, "INDEX200_1", "시장 지수 조회에 성공하였습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
