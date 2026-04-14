package juby.invest.ta4j.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

@Slf4j
@Component
@Order(1)
public class SmaStrategy implements BacktestStrategy{

    /***
     * 함수: SMA(단순이평선) 전략을 정의해주는 함수
     * @param series BarSeries
     * @return Strategy
     */
    @Override
    public Strategy strategy(BarSeries series){

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series); // 종가 지표
        SMAIndicator shortSma = new SMAIndicator(closePrice, 5); // 5일 이평선
        SMAIndicator longSma = new SMAIndicator(closePrice, 20); // 20일 이평선

        Rule breakout = new CrossedUpIndicatorRule(shortSma, longSma);
        Rule pullback = new CrossedDownIndicatorRule(shortSma, longSma);
        
        log.info("SMA 교차 전략 실행");
        return new BaseStrategy("SMA 교차 전략", breakout, pullback);
    }
}
