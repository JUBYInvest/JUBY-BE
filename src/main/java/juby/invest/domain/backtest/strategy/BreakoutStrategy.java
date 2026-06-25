package juby.invest.domain.backtest.strategy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.ta4j.core.*;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverOrEqualIndicatorRule;

@Component
@Order(5)
public class BreakoutStrategy implements BacktestStrategy{

    @Override
    public Strategy strategy(BarSeries series) {

        // 종가 지표
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        // 거래량 지표
        VolumeIndicator volume = new VolumeIndicator(series);
        // 최근 20일 최고가 지표
        HighestValueIndicator highestValue = new HighestValueIndicator(closePrice, 20);
        // 전일까지의 20일 최고가 지표
        PreviousValueIndicator previousValue = new PreviousValueIndicator(highestValue, 1);
        // 최근 20일 거래량 지표
        SMAIndicator sma = new SMAIndicator(volume, 20);
        // 최근 14일 평균 변동폭
        ATRIndicator atr = new ATRIndicator(series, 14);

        // 진입조건 1. 종가가 최근 20일(전일까지) 최고가를 상향돌파하고,
        //         2. 거래량이 최근 20일 평균 거래량의 1.5배 이상
        Rule entryRule_1 = new CrossedUpIndicatorRule(closePrice, previousValue);
        Rule entryRule_2 = new Rule() {
            @Override
            public boolean isSatisfied(int index, TradingRecord tradingRecord) {
                // 당일 거래량
                Num curVolume = volume.getValue(index);

                // 당일 기준 20일 평균 거래량
                Num avgVolume = sma.getValue(index);

                // 평균 거래량의 1.5배
                Num threshold = avgVolume.multipliedBy(series.numFactory().numOf(1.5));

                return curVolume.isGreaterThanOrEqual(threshold);
            }
        };
        Rule entryRule = entryRule_1.and(entryRule_2);

        // 매도 조건 1. 종가가 돌파 기준선 아래로 하락하거나,
        //          2. 종가가 (매수가 - 2 * ATR(14))이하로 하락
        Rule exitRule_1 = new CrossedDownIndicatorRule(closePrice, previousValue);
        Rule exitRule_2 = new Rule() {
            @Override
            public boolean isSatisfied(int index, TradingRecord tradingRecord) {

                if (tradingRecord == null || !tradingRecord.getCurrentPosition().isOpened()){
                    return false;
                }

                // 당일 종가
                ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
                Num closePrice1 = closePrice.getValue(index);

                // 당일 매수가
                Num entryPrice = tradingRecord.getCurrentPosition().getEntry().getPricePerAsset();

                // 당일 ATR(14)
                Num currentAtr = atr.getValue(index);

                // 손절가 = 매수가 - (2*ATR(14))
                Num stopPrice = entryPrice.minus(currentAtr.multipliedBy(series.numFactory().numOf(2)));

                return closePrice1.isLessThanOrEqual(stopPrice);
            }
        };
        Rule exitRule = exitRule_1.and(exitRule_2);

        return new BaseStrategy("돌파 전략", entryRule, exitRule);
    }
}
