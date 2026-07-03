package juby.invest.domain.backtest.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

@Slf4j
@Component
@Order(3)
public class SmaStrategy implements BacktestStrategy{

    /***
     * 함수: SMA(단순이평선) 전략을 정의해주는 함수
     */
    @Override
    public Strategy strategy(BarSeries series){

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series); // 종가 지표
        SMAIndicator shortSma = new SMAIndicator(closePrice, 20); // 20일 이평선
        SMAIndicator longSma = new SMAIndicator(closePrice, 60); // 60일 이평선

        // 매수 조건: 단기 이평선이 장기 이평선을 상향돌파
        Rule entryRule = new CrossedUpIndicatorRule(shortSma, longSma);

        // 매도 조건: 단기 이평선이 장기 이평선을 하향돌파
        Rule exitRule = new CrossedDownIndicatorRule(shortSma, longSma);


        return new BaseStrategy("SMA 교차 전략", entryRule, exitRule);
    }
}
