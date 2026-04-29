package juby.invest.domain.backtest.strategy;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

public interface BacktestStrategy {
    Strategy strategy(BarSeries series);
}
