package juby.invest.domain.backtest.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
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

    /***
     * TODO(#109 후속 이슈): EMA 5·20 lookback에 맞는 워밍업 봉 수를 정의한다.
     * 지금은 0을 반환해 기존 동작(요청 구간만으로 지표 계산)을 그대로 유지한다.
     */
    @Override
    public int warmupBars() {
        return 0;
    }
}
