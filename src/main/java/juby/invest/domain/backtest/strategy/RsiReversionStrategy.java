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
@Order(3)
public class RsiReversionStrategy implements BacktestStrategy {

    @Override
    public Strategy strategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);

        Rule entryRule = new CrossedUpIndicatorRule(rsi, 40);
        Rule exitRule = new CrossedDownIndicatorRule(rsi, 70);

        return new BaseStrategy("RSI 역추세 전략", entryRule, exitRule);
    }
}
