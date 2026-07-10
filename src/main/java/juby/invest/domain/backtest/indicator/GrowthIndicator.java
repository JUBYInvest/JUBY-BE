package juby.invest.domain.backtest.indicator;

import juby.invest.domain.backtest.dto.BacktestResDto;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

@Component
public class GrowthIndicator {

    /***
     * 성장성 축
     * 모멘텀 수익률(최근 1달), 거래량 증가율(5일, 20일), 거래 횟수
     */
    public BacktestResDto.QuantScoringResponse.Growth indicate(BarSeries series, TradingRecord record){

        Num momentumRatio = calculateMomentumRatio(series, 20);

        Num volGrowthRatio = calculateVolGrowthRatio(series);

        return BacktestResDto.QuantScoringResponse.Growth.builder()
                .momentumRatio(momentumRatio.bigDecimalValue())
                .volGrowthRatio(volGrowthRatio.bigDecimalValue())
                .positionCount(record.getPositionCount())
                .build();
    }

    /**
     * 모멘텀 수익률: 최근 1개월 동안 가격이 얼마나 올랐는지
     * (현재가 - 1개월 전 가격) / 1개월 전 가격
     */
    private Num calculateMomentumRatio(BarSeries series, int lookBackDays){

        int barCount = series.getBarCount();

        if (barCount < lookBackDays){
            lookBackDays = Math.max(1, barCount);
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        ROCIndicator roc = new ROCIndicator(closePrice, lookBackDays);

        Num rawMomentum = roc.getValue(series.getEndIndex());

        return rawMomentum.dividedBy(series.numFactory().numOf(100));
    }

    /***
     * 거래량 증가율: 최근 거래량(5일)이 과거 장기 평균 거래량(20일) 대비 얼마나 증가했는지
     * (단기평균거래량 - 장기평균거래량) / 장기평균거래량
     */
    private Num calculateVolGrowthRatio(BarSeries series){

        // 전체 거래량 지표
        VolumeIndicator volumeIndicator = new VolumeIndicator(series);

        // 단기거래량지표
        SMAIndicator shortSmaIndicator = new SMAIndicator(volumeIndicator, 5);
        // 장기거래량지표
        SMAIndicator longSmaIndicator = new SMAIndicator(volumeIndicator, 20);

        // 마지막 봉 기준 단기/장기 평균 거래량 추출
        Num shortAvgVolume = shortSmaIndicator.getValue(series.getEndIndex());
        Num longAvgVolume = longSmaIndicator.getValue(series.getEndIndex());

        // (단기 평균 거래량 - 장기평균거래량) / 장기평균거래량
        return shortAvgVolume.minus(longAvgVolume).dividedBy(longAvgVolume);
    }
}
