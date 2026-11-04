package juby.invest.domain.marketindex.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MarketIndexErrorCode implements BaseErrorCode {

    // 실시간도 REST도 모두 실패한 경우. KIS 연동 자체가 흔들린 상황이라 5xx로 둔다.
    INDEX_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "INDEX502_1", "시장 지수 조회에 실패하였습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
