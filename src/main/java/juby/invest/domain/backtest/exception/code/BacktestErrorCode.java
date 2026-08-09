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
            "백테스트 기능을 이용할 수 없습니다. 로그인 후 이용해주세요."),
    STOCKCODE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "BACKTEST404_1",
            "해당 종목이 존재하지 않습니다. 정확한 종목코드를 입력해주세요."),
    STRATEGY_NOT_FOUND(HttpStatus.NOT_FOUND,
            "BACKTEST404_2",
            "해당 전략이 존재하지 않습니다. 정확한 전략명을 입력해주세요."),
    INVEST_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "BACKTEST404_3",
            "해당 투자 성향이 존재하지 않습니다. 1 ~ 5 사이 숫자를 입력해주세요."),
    DATE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "BACKTEST404_4",
            "요청한 날짜의 주식 데이터가 DB에 존재하지 않습니다."),
    PERIOD_NOT_SUPPORTED(HttpStatus.BAD_REQUEST,
            "BACKTEST400_1",
            "해당 투자성향 전략에서는 지원하지 않는 기간 프리셋입니다. 지표 계산에 필요한 최소 기간 이상을 선택해주세요."),
    PRESET_NOT_FOUND(HttpStatus.NOT_FOUND,
            "BACKTEST404_5",
            "아직 계산되지 않은 프리셋입니다. 잠시 후 다시 시도해주세요.")
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
