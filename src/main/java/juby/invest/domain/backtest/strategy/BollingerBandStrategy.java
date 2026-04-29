package juby.invest.domain.backtest.strategy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

@Component
@Order(4)
public class BollingerBandStrategy implements BacktestStrategy{

    @Override
    public Strategy strategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 1. 표준편차 (20일)
        StandardDeviationIndicator sd = new StandardDeviationIndicator(closePrice, 20);

        // 2. 중심선 (명시적으로 SMA 20일 설정)
        SMAIndicator sma = new SMAIndicator(closePrice, 20);
        BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(sma);

        // 3. 상/하단 밴드 (기본적으로 2배수 표준편차 적용)
        BollingerBandsUpperIndicator upper = new BollingerBandsUpperIndicator(middle, sd);
        BollingerBandsLowerIndicator lower = new BollingerBandsLowerIndicator(middle, sd);

        // 4. 규칙 설정
        // 진입: 종가가 상단을 '상향' 돌파할 때
        Rule entryRule = new CrossedUpIndicatorRule(closePrice, upper);

        // 탈출: 종가가 '중심선'을 '하향' 돌파할 때 (현실적인 수익 보존)
        // 하단(lower)까지 기다리는 것보다 훨씬 매매 횟수가 늘어납니다.
        Rule exitRule = new CrossedDownIndicatorRule(closePrice, middle);

        Strategy strategy = new BaseStrategy("볼린저 밴드 돌파 전략", entryRule, exitRule);

        // 5. 매우 중요: 지표가 계산될 수 있도록 최소 20봉 이상의 여유를 줍니다.
        strategy.setUnstableBars(20);

        return strategy;
    }
}
