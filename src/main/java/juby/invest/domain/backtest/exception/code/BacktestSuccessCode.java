package juby.invest.domain.backtest.exception.code;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BacktestSuccessCode implements BaseSuccessCode {

    OK(HttpStatus.OK,"BACKTEST200_1", "백테스트가 성공적으로 실행되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
