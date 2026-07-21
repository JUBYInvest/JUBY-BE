package juby.invest.domain.backtest.service;

import jakarta.transaction.Transactional;
import juby.invest.domain.backtest.dto.BacktestReqDto;
import juby.invest.domain.backtest.exception.BacktestException;
import juby.invest.domain.backtest.exception.code.BacktestErrorCode;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.exception.code.StockErrorCode;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.backtest.converter.AnalysisCriterionConverter;
import juby.invest.domain.backtest.converter.BarSeriesConverter;
import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.strategy.BacktestStrategy;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {

    private final BarSeriesConverter barSeriesConverter;
    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final Map<String, BacktestStrategy> strategyMap;
    private final AnalysisCriterionConverter analysisCriterionConverter;

    /***
     * 전달받은 종목코드, 전략, 시작일, 종료일을 기준으로 백테스트 전략을 수행한다.
     * @param dto BacktestReqDto.ReqInfo
     * @return BacktestResDto.GetInfo
     */
    @Transactional
    public BacktestResDto.QuantScoringResponse runStrategy(BacktestReqDto.ReqInfo dto){

        String stockCode = stockRepository.findById(dto.stockCode())
                .orElseThrow(() -> new BacktestException(BacktestErrorCode.STOCKCODE_NOT_FOUND)).getStockCode();
        int investType = dto.investType();
        LocalDate startDate = dto.startDate();
        LocalDate endDate = dto.endDate();

        // List<DailyPrice -> ta4j의 Barseries 변환
        List<DailyPrice> dailyPriceList = dailyPriceRepository.findByStock_StockCodeAndDateBetweenOrderByDateAsc(stockCode, startDate, endDate);

        if (dailyPriceList.isEmpty()){
            throw new BacktestException(BacktestErrorCode.DATE_NOT_FOUND);
        }

        BarSeries series = barSeriesConverter.barSeriesConverter(dailyPriceList, stockCode);

        // 실행할 전략명 찾기
        String strategyName = switch (investType) {
            case 1 -> "rsiReversionStrategy"; // 안정형 -> RSI역추세 전략
            case 2 -> "bollingerBandStrategy"; // 안정추구형 -> 볼린저밴드 전략
            case 3 -> "smaStrategy"; // 위험중립형 -> SMA이평선 전략
            case 4 -> "macdTrendStrategy"; // 적극투자형 -> MACD 추세추종 전략
            case 5 -> "breakoutStrategy"; // 공격투자형 -> 돌파 전략
            default -> throw new BacktestException(BacktestErrorCode.INVEST_TYPE_NOT_FOUND);
        };

        // 전략 구축
        Strategy strategy;
        try {
            strategy = strategyMap.get(strategyName).strategy(series);
        } catch (NullPointerException e){
            throw new BacktestException(BacktestErrorCode.STRATEGY_NOT_FOUND);
        }

        // 구축된 전략을 바탕으로 백테스팅 수행
        BarSeriesManager manager = new BarSeriesManager(series);
        TradingRecord record = manager.run(strategy);

        log.info("체결된 포지션 개수: {}", record.getPositionCount());
        log.info("포지션 내역: {}", record.getPositions());

        // 평가 지표 산출 (안정성, 수익성, 효율성, 성장성)
        return analysisCriterionConverter.converter(stockCode, investType, series, record);
    }
}
