package juby.invest.ta4j.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

@Slf4j
@Component
public class EmaStrategy implements BacktestStrategy{

    /**
     * 함수: EMA 교차 전략을 정의한다.
     * @param series BarSeries
     * @return EMA 교차 전략
     */
    @Override
    public Strategy strategy(BarSeries series) {

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator shortEma = new EMAIndicator(closePrice, 5);
        EMAIndicator longEma = new EMAIndicator(closePrice, 20);

        Rule breakout = new CrossedUpIndicatorRule(shortEma, longEma);
        Rule pullback = new CrossedDownIndicatorRule(shortEma, longEma);

        log.info("EMA 교차 전략 실행");
        return new BaseStrategy("EMA 교차 전략", breakout, pullback);
    }
}
