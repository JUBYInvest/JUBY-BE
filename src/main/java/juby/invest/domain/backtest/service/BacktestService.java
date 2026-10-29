package juby.invest.domain.backtest.service;

import jakarta.transaction.Transactional;
import juby.invest.domain.backtest.exception.BacktestException;
import juby.invest.domain.backtest.exception.code.BacktestErrorCode;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.backtest.converter.AnalysisCriterionConverter;
import juby.invest.domain.backtest.converter.BarSeriesConverter;
import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.strategy.BacktestStrategy;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.num.Num;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {

    private final BarSeriesConverter barSeriesConverter;
    private final DailyPriceRepository dailyPriceRepository;
    private final Map<String, BacktestStrategy> strategyMap;
    private final AnalysisCriterionConverter analysisCriterionConverter;

    /***
     * 함수 기능: 전달받은 종목코드, 투자성향, 시작일, 종료일을 기준으로 백테스트 전략을 수행한다.
     *           각 전략에 맞는 워밍업 봉 수를 추가하여 전략을 실행한다.
     * @param stock 종목
     * @param investType 투자 성향 (1~5)
     * @param startDate 시작일
     * @param endDate 종료일
     * @return BacktestResDto.QuantScoringResponse
     */
    @Transactional
    public BacktestResDto.QuantScoringResponse runStrategy(
            Stock stock, int investType, LocalDate startDate, LocalDate endDate){

        // investType과 일치하는 전략 빈을 먼저 찾는다.
        BacktestStrategy backtestStrategy = strategyMap.get(resolveStrategyName(investType));

        if (backtestStrategy == null){
            throw new BacktestException(BacktestErrorCode.STRATEGY_NOT_FOUND);
        }

        // 사용자 요청 구간의 일봉 리스트를 가져온다.
        List<DailyPrice> requestedPrices =
                dailyPriceRepository.findByStockAndDateBetweenOrderByDateAsc(stock, startDate, endDate);

        if (requestedPrices.isEmpty()){
            throw new BacktestException(BacktestErrorCode.DATE_NOT_FOUND);
        }

        // 워밍업 구간 일봉 (시작일 이전 N거래일)
        List<DailyPrice> warmupPrices = findWarmupPrices(stock, startDate, backtestStrategy.warmupBars());

        // 워밍업 + 요청 구간을 날짜 오름차순으로 결합해 ta4j의 BarSeries로 변환
        List<DailyPrice> seriesPrices = new ArrayList<>(warmupPrices.size() + requestedPrices.size());
        seriesPrices.addAll(warmupPrices);
        seriesPrices.addAll(requestedPrices);

        BarSeries series = barSeriesConverter.barSeriesConverter(seriesPrices, stock.getStockCode());

        // 전략 실행 시작 인덱스
        int evalStartIndex = warmupPrices.size();

        // 전략 구축
        Strategy strategy = backtestStrategy.strategy(series);

        // 워밍업 구간의 신호 차단 (BaseStrategy는 index < unstableBars 구간을 불안정 구간으로 본다)
        if (evalStartIndex > 0){
            strategy.setUnstableBars(evalStartIndex);
        }

        // 구축된 전략을 바탕으로 요청 구간에 대해서만 백테스팅 수행
        BarSeriesManager manager = new BarSeriesManager(series);
        TradingRecord record = manager.run(strategy, evalStartIndex, series.getEndIndex());

        logPositions(series, record);

        // 평가 지표 산출 (안정성, 수익성, 효율성, 성장성) - 요청 구간 기준
        return analysisCriterionConverter.converter(stock, investType, series, record, evalStartIndex);
    }

    /***
     * 함수 기능: 체결 내역을 로그로 남긴다.
     *           TradingRecord는 BarSeries의 index만 들고 있어 체결 시점이 드러나지 않으므로
     *           index를 실제 거래일로 되돌려 찍는다.
     */
    private void logPositions(BarSeries series, TradingRecord record){

        log.info("체결된 포지션 개수: {}", record.getPositionCount());

        for (Position position : record.getPositions()) {
            Num entryPrice = position.getEntry().getNetPrice();
            Num exitPrice = position.getExit().getNetPrice();
            Num returnRate = exitPrice.minus(entryPrice).dividedBy(entryPrice)
                    .multipliedBy(series.numFactory().numOf(100));

            log.info("체결 내역 - 매수 {} ({}) -> 매도 {} ({}), 수익률 {}%",
                    tradeDate(series, position.getEntry().getIndex()), entryPrice,
                    tradeDate(series, position.getExit().getIndex()), exitPrice,
                    returnRate);
        }

        logOpenPosition(series, record);
    }

    /***
     * 함수 기능: 매수 후 매도 신호가 나오지 않아 요청 구간이 끝날 때까지 열려 있는 포지션을 로그로 남긴다.
     */
    private void logOpenPosition(BarSeries series, TradingRecord record){

        Position currentPosition = record.getCurrentPosition();

        if (currentPosition == null || !currentPosition.isOpened()){
            return;
        }

        Num entryPrice = currentPosition.getEntry().getNetPrice();

        // 미청산이라 실현 수익률이 없다. 요청 구간 마지막 봉의 종가로 평가만 해둔다.
        Num lastClosePrice = series.getBar(series.getEndIndex()).getClosePrice();
        Num valuationRate = lastClosePrice.minus(entryPrice).dividedBy(entryPrice)
                .multipliedBy(series.numFactory().numOf(100));

        log.info("미청산 내역 - 매수 {} ({}), 매도 신호 없음. {} 종가 {} 기준 평가수익률 {}%",
                tradeDate(series, currentPosition.getEntry().getIndex()), entryPrice,
                tradeDate(series, series.getEndIndex()), lastClosePrice, valuationRate);
    }

    /***
     * 함수 기능: BarSeries의 index를 해당 봉의 거래일로 되돌린다.
     *           봉의 종료 시각을 15:30 KST로 넣었으므로 같은 시간대로 되돌려 읽는다.
     */
    private LocalDate tradeDate(BarSeries series, int index){

        return LocalDate.ofInstant(series.getBar(index).getEndTime(), ZoneId.of("Asia/Seoul"));
    }

    /***
     * 함수 기능: 투자성향에 대응하는 전략 빈 이름을 반환한다.
     */
    private String resolveStrategyName(int investType){

        return switch (investType) {
            case 1 -> "rsiReversionStrategy"; // 안정형 -> RSI역추세 전략
            case 2 -> "bollingerBandStrategy"; // 안정추구형 -> 볼린저밴드 전략
            case 3 -> "smaStrategy"; // 위험중립형 -> SMA이평선 전략
            case 4 -> "macdTrendStrategy"; // 적극투자형 -> MACD 추세추종 전략
            case 5 -> "breakoutStrategy"; // 공격투자형 -> 돌파 전략
            default -> throw new BacktestException(BacktestErrorCode.INVEST_TYPE_NOT_FOUND);
        };
    }

    /***
     * 함수 기능: 시작일 이전 N거래일의 일봉을 날짜 오름차순으로 조회한다.
     */
    private List<DailyPrice> findWarmupPrices(Stock stock, LocalDate startDate, int warmupBars){

        if (warmupBars <= 0){
            return List.of();
        }

        // 시작일 이전 N거래일의 일봉 데이터를 최신순으로 가져온다.
        List<DailyPrice> warmupPrices = new ArrayList<>(dailyPriceRepository
                .findByStockAndDateLessThanOrderByDateDesc(stock, startDate, Limit.of(warmupBars)));

        // 가져온 워밍업 일봉이 요구하는 일봉 갯수에 미치지 못할 경우 예외를 반환한다.
        if (warmupPrices.size() < warmupBars){
            log.warn("워밍업 일봉 부족. 종목코드: {}, 시작일: {}, 필요: {}봉, 확보: {}봉",
                    stock.getStockCode(), startDate, warmupBars, warmupPrices.size());
            throw new BacktestException(BacktestErrorCode.WARMUP_DATA_NOT_ENOUGH);
        }

        // 최신순으로 조회되므로 날짜 오름차순으로 뒤집는다.
        Collections.reverse(warmupPrices);

        return warmupPrices;
    }
}
