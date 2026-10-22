package juby.invest.domain.backtest.strategy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

@Component
@Order(4)
public class MacdTrendStrategy implements BacktestStrategy{

    @Override
    public Strategy strategy(BarSeries series) {

        // 종가 지표
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // MACD 선 (12일 EMA - 26일 EMA)
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        // signal 선 (MACD 선의 9일 EMA)
        EMAIndicator signal = new EMAIndicator(macd, 9);

        // 매수 조건: MACD 선이 signal선을 상향돌파
        Rule entryRule = new CrossedUpIndicatorRule(macd, signal);
        // 매도 조건: MACD 선이 signal선을 하향돌파
        Rule exitRule = new CrossedDownIndicatorRule(macd, signal);

        return new BaseStrategy("Macd 추세추종 전략", entryRule, exitRule);
    }

    /***
     * TODO(#109 후속 이슈): MACD 12·26·9 lookback에 맞는 워밍업 봉 수를 정의한다.
     * 지금은 0을 반환해 기존 동작(요청 구간만으로 지표 계산)을 그대로 유지한다.
     */
    @Override
    public int warmupBars() {
        return 0;
    }
}
