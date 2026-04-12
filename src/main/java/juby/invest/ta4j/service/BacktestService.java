package juby.invest.ta4j.service;

import jakarta.transaction.Transactional;
import juby.invest.domain.DailyPrice;
import juby.invest.repository.DailyPriceRepository;
import juby.invest.ta4j.converter.BarSeriesConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {

    private final BarSeriesConverter barSeriesConverter;
    private final DailyPriceRepository dailyPriceRepository;

    @Transactional
    public BarSeries runStrategy(String stockCode){

        List<DailyPrice> dailyPriceList = dailyPriceRepository.findByStockStockCodeOrderByDateAsc(stockCode);
        BarSeries series = barSeriesConverter.convert(dailyPriceList, stockCode);

        log.info("변환된 Bar 개수: {}", series.getBarCount());
        return series;
    }
}
