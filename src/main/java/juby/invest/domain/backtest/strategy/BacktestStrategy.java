package juby.invest.domain.backtest.strategy;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

public interface BacktestStrategy {

    Strategy strategy(BarSeries series); // 실행 전략 (RSI 역추세, 볼린저밴드, SMA, MACD, 돌파)

    int warmupBars(); // 전략 실행을 위한 워밍업 봉
}
