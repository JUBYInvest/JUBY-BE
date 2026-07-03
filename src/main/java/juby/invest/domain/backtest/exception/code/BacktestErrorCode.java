package juby.invest.domain.backtest.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BacktestErrorCode implements BaseErrorCode {

    BACKTEST_FORBIDDEN(HttpStatus.FORBIDDEN,
            "BACKTEST403_1",
            "백테스트가 금지되었습니다."),
    STOCKCODE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "BACKTEST404_1",
            "해당 종목이 존재하지 않습니다. 정확한 종목코드를 입력해주세요."),
    STRATEGY_NOT_FOUND(HttpStatus.NOT_FOUND,
            "BACKTEST404_2",
            "해당 전략이 존재하지 않습니다. 정확한 전략명을 입력해주세요."),
    INVEST_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "BACKTEST404_3",
            "해당 투자 성향이 존재하지 않습니다. 1 ~ 5 사이 숫자를 입력해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
