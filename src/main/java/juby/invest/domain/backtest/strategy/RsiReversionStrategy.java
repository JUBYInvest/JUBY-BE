package juby.invest.domain.backtest.strategy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

@Component
@Order(1)
public class RsiReversionStrategy implements BacktestStrategy {

    @Override
    public Strategy strategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);

        Rule entryRule = new CrossedUpIndicatorRule(rsi, 40); // 매수 조건
        Rule exitRule = new CrossedDownIndicatorRule(rsi, 70); // 매도 조건

        return new BaseStrategy("RSI 역추세 전략", entryRule, exitRule);
    }

    /***
     * TODO(#109 후속 이슈): RSI 14 lookback에 맞는 워밍업 봉 수를 정의한다.
     * 지금은 0을 반환해 기존 동작(요청 구간만으로 지표 계산)을 그대로 유지한다.
     */
    @Override
    public int warmupBars() {
        return 0;
    }
}
