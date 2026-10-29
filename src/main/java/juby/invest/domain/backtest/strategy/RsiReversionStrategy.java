package juby.invest.domain.backtest.strategy;

import juby.invest.domain.backtest.indicator.ta4j.WilderRsiIndicator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

@Component
@Order(1)
public class RsiReversionStrategy implements BacktestStrategy {

    private static final int RSI_BAR_COUNT = 14;

    @Override
    public Strategy strategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // ta4j 기본 RSIIndicator는 평균 상승/하락 시드를 '한 봉'으로 잡아 초반 수십 봉이 통째로 틀어진다.
        // 와일더 원래 방식(첫 14개 변화량의 단순 평균)으로 시드를 잡는 구현을 쓴다.
        WilderRsiIndicator rsi = new WilderRsiIndicator(closePrice, RSI_BAR_COUNT);

        // 매수: RSI가 40 아래로 내려가면 (과매도 진입)
        Rule entryRule = new CrossedDownIndicatorRule(rsi, 40);

        // 매도: RSI가 70 위로 올라서면 (과매수 진입)
        Rule exitRule = new CrossedUpIndicatorRule(rsi, 70);

        // 체결은 BarSeriesManager의 기본 TradeOnNextOpenModel이 처리한다 -> 신호 다음 봉의 시가
        return new BaseStrategy("RSI 역추세 전략", entryRule, exitRule);
    }
    
    @Override
    public int warmupBars() {
        return 150;
    }
}
