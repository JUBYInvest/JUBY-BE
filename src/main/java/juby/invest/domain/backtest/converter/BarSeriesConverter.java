package juby.invest.domain.backtest.converter;

import juby.invest.domain.stock.entity.DailyPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
public class BarSeriesConverter {

    /***
     * 기능: List<DailyPrice> -> BarSeries로 변환
     * @param dailyPriceList 한 종목 기간별 OHLVC 데이터
     * @param stockCode 종목 코드 (ex: 005930)
     * @return BarSeries
     */
    public BarSeries barSeriesConverter(List<DailyPrice> dailyPriceList, String stockCode){

        BarSeries series = new BaseBarSeriesBuilder().withName(stockCode).build();

        ZoneId kstZone = ZoneId.of("Asia/Seoul");

        try {
            for (DailyPrice dailyPrice : dailyPriceList) {
                Instant endTime = Instant.from(dailyPrice.getDate().atTime(15, 30).atZone(kstZone));

                series.addBar(series.barBuilder()
                        .timePeriod(Duration.ofDays(1))
                        .endTime(endTime)
                        .openPrice(dailyPrice.getOpenPrice())
                        .highPrice(dailyPrice.getHighPrice())
                        .lowPrice(dailyPrice.getLowPrice())
                        .closePrice(dailyPrice.getClosePrice())
                        .volume(dailyPrice.getVolume())
                        .build());
            }
            log.info("BarSeries 변환 성공");

        } catch (Exception e){
            throw new RuntimeException("BarSeries 변환 에러 발생." + e.getMessage());
        }

        return series;
    }
}
