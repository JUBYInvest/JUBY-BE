package juby.invest.ta4j.service;

import jakarta.transaction.Transactional;
import juby.invest.domain.DailyPrice;
import juby.invest.repository.DailyPriceRepository;
import juby.invest.ta4j.converter.AnalysisCriterionConverter;
import juby.invest.ta4j.converter.BarSeriesConverter;
import juby.invest.ta4j.dto.BacktestResponseDto;
import juby.invest.ta4j.strategy.BacktestStrategy;
import juby.invest.ta4j.strategy.SmaStrategy;
import juby.invest.ta4j.strategy.StrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {

    private final BarSeriesConverter barSeriesConverter;
    private final DailyPriceRepository dailyPriceRepository;
    private final StrategyFactory strategyFactory;
    private final AnalysisCriterionConverter analysisCriterionConverter;

    /***
     * 함수: 종목코드와 전략 이름을 바탕으로 전략을 실행한다.
     * @param stockCode 종목 코드 (ex 005930)
     */
    @Transactional
    public BacktestResponseDto runStrategy(String stockCode, int strategyNum){

        // List<DailyPrice -> ta4j의 Barseries 변환
        List<DailyPrice> dailyPriceList = dailyPriceRepository.findByStockStockCodeOrderByDateAsc(stockCode);
        BarSeries series = barSeriesConverter.convert(dailyPriceList, stockCode);

        // 전략 구축 및 실행
        Strategy strategy = strategyFactory.getStrategy(strategyNum).strategy(series);
        BarSeriesManager manager = new BarSeriesManager(series);
        TradingRecord record = manager.run(strategy);

        System.out.println("체결된 포지션: " + record.getPositionCount());
        System.out.println("포지션 entry 한 날짜" + record.getLastEntry());
        System.out.println("포지션 exit 한 날짜" + record.getLastExit());
        // 결과 지표 분석
        List<BigDecimal> analysisList = analysisCriterionConverter.converter(series, record);

        return BacktestResponseDto.builder()
                .stockCode(stockCode)
                .strategyName(strategy.getName())
                .totalReturn(analysisList.get(0))
                .annualizedReturn(analysisList.get(1))
                .positionCount(record.getPositionCount())
                .sharpeRatio(analysisList.get(2))
                .stdDeviation(analysisList.get(3))
                .maxDrawdown(analysisList.get(4))
                .build();
    }
}
