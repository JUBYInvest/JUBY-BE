package juby.invest.ta4j.strategy;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

public interface BacktestStrategy {
    Strategy strategy(BarSeries series);
}
