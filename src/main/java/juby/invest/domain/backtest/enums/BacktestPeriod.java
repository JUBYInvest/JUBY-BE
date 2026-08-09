package juby.invest.domain.backtest.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Getter
@RequiredArgsConstructor
public enum BacktestPeriod {

    ONE_MONTH(1, "1개월"),
    THREE_MONTHS(3, "3개월"),
    SIX_MONTHS(6, "6개월"),
    ONE_YEAR(12, "1년");

    private final int months;
    private final String label;

    /***
     * 함수 기능: 기준일(endDate)로부터 이 프리셋 기간만큼 거슬러 올라간 시작일을 계산한다.
     * @param endDate 기준일 (보통 해당 종목의 최신 일봉 날짜)
     * @return 시작일
     */
    public LocalDate calculateStartDate(LocalDate endDate) {
        return endDate.minusMonths(months);
    }
}
