package juby.invest.domain.backtest.service;

import jakarta.transaction.Transactional;
import juby.invest.domain.backtest.exception.BacktestException;
import juby.invest.domain.backtest.exception.code.BacktestErrorCode;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.backtest.converter.AnalysisCriterionConverter;
import juby.invest.domain.backtest.converter.BarSeriesConverter;
import juby.invest.domain.backtest.dto.BacktestResDTO;
import juby.invest.domain.backtest.strategy.BacktestStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {

    private final BarSeriesConverter barSeriesConverter;
    private final DailyPriceRepository dailyPriceRepository;
    private final AnalysisCriterionConverter analysisCriterionConverter;
    private final Map<String, BacktestStrategy> strategyMap;

    /***
     * 함수: 종목코드와 전략 이름을 바탕으로 전략을 실행한다.
     * @param stockCode 종목 코드 (ex 005930)
     */
    @Transactional
    public BacktestResDTO.GetInfo runStrategy(String stockCode, String strategyName){

        // List<DailyPrice -> ta4j의 Barseries 변환
        List<DailyPrice> dailyPriceList = dailyPriceRepository.findByStockStockCodeOrderByDateAsc(stockCode);

        if (dailyPriceList.isEmpty()){
            throw new BacktestException(BacktestErrorCode.STOCKCODE_NOT_FOUND);
        }

        BarSeries series = barSeriesConverter.convert(dailyPriceList, stockCode);

        // 전략 구축 및 실행
        Strategy strategy = null;
        try {
            strategy = strategyMap.get(strategyName).strategy(series);
        } catch (NullPointerException e){
            throw new BacktestException(BacktestErrorCode.STRATEGY_NOT_FOUND);
        }

        BarSeriesManager manager = new BarSeriesManager(series);
        TradingRecord record = manager.run(strategy);

        System.out.println("체결된 포지션: " + record.getPositionCount());
        System.out.println("포지션 entry 한 날짜" + record.getLastEntry());
        System.out.println("포지션 exit 한 날짜" + record.getLastExit());
        // 결과 지표 분석
        List<BigDecimal> analysisList = analysisCriterionConverter.converter(series, record);

        return BacktestResDTO.GetInfo.builder()
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
