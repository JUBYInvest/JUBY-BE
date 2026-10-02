package juby.invest.domain.backtest.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LeadingStockTheme {

    TECH("기술주", "000660"), // SK하이닉스
    DEFENSE("방산주", "012450"), // 한화에어로스페이스
    BIO("바이오주", "207940"); // 삼성바이오로직스

    private final String label;
    private final String stockCode;
}
