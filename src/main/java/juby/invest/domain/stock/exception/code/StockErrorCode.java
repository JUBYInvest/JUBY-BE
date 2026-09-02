package juby.invest.domain.stock.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StockErrorCode implements BaseErrorCode {
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "STOCK404_1", "해당 종목을 찾을 수 없습니다. 정확한 종목명을 입력해주세요."),
    DAILYPRICE_NOT_FOUND(HttpStatus.NOT_FOUND,"STOCK404_2","해당 일봉을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
