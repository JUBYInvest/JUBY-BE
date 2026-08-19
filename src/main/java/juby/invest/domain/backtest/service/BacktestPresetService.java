package juby.invest.domain.backtest.service;

import juby.invest.domain.backtest.converter.BacktestPresetConverter;
import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.entity.BacktestPresetResult;
import juby.invest.domain.backtest.enums.BacktestPeriod;
import juby.invest.domain.backtest.exception.BacktestException;
import juby.invest.domain.backtest.exception.code.BacktestErrorCode;
import juby.invest.domain.backtest.repository.BacktestPresetResultRepository;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestPresetService {

    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final BacktestPresetResultRepository presetRepository;
    private final BacktestService backtestService;
    private final BacktestPresetConverter presetConverter;

    // 투자성향(전략)별로 지표 계산에 필요한 최소 lookback을 감안한 최소 허용 기간 프리셋
    // 1(RSI 14) / 2(볼린저 20) / 3(SMA 20·60) / 4(MACD 12·26·9) / 5(돌파 20·ATR14)
    private static final Map<Integer, BacktestPeriod> MIN_PERIOD = Map.of(
            1, BacktestPeriod.ONE_MONTH,
            2, BacktestPeriod.THREE_MONTHS,
            3, BacktestPeriod.SIX_MONTHS,
            4, BacktestPeriod.THREE_MONTHS,
            5, BacktestPeriod.THREE_MONTHS
    );

    /***
     * 함수 기능: 종목코드, 투자성향, 기간 프리셋으로 미리 계산된 백테스트 결과를 조회한다.
     */
    public BacktestResDto.PresetResponse getPreset(String stockCode, int investType, BacktestPeriod period) {

        if (!stockRepository.existsById(stockCode)) {
            throw new BacktestException(BacktestErrorCode.STOCKCODE_NOT_FOUND);
        }

        if (!isSupported(investType, period)) {
            throw new BacktestException(BacktestErrorCode.PERIOD_NOT_SUPPORTED);
        }

        BacktestPresetResult entity = presetRepository
                .findByStock_StockCodeAndInvestTypeAndPeriod(stockCode, investType, period)
                .orElseThrow(() -> new BacktestException(BacktestErrorCode.PRESET_NOT_FOUND));

        return presetConverter.toResponse(entity);
    }

    /***
     * 함수 기능: 투자성향이 해당 기간 프리셋을 지원하는지 확인한다.
     */
    public boolean isSupported(int investType, BacktestPeriod period) {
        BacktestPeriod minPeriod = MIN_PERIOD.get(investType);
        if (minPeriod == null) {
            throw new BacktestException(BacktestErrorCode.INVEST_TYPE_NOT_FOUND);
        }
        return period.ordinal() >= minPeriod.ordinal();
    }

    /***
     * 함수 기능: 투자성향별로 실제 DB에 적재되어 선택 가능한 기간 프리셋 목록을 조회한다. (종목 무관 global 기준)
     * 프론트에서 전략 선택 시 기간 선택지를 이 목록으로 제한하는 데 사용한다.
     */
    public List<BacktestResDto.PresetOptionsResponse> getPresetOptions() {

        List<BacktestResDto.PresetOptionsResponse> options = new ArrayList<>();

        for (int investType = 1; investType <= 5; investType++) {
            List<BacktestPeriod> availablePeriods = presetRepository.findDistinctPeriodsByInvestType(investType);

            List<BacktestResDto.PresetOptionsResponse.PeriodOption> periodOptions = Arrays.stream(BacktestPeriod.values())
                    .filter(availablePeriods::contains) // enum 선언 순서(1개월→1년)대로 정렬
                    .map(period -> BacktestResDto.PresetOptionsResponse.PeriodOption.builder()
                            .period(period)
                            .label(period.getLabel())
                            .build())
                    .toList();

            options.add(BacktestResDto.PresetOptionsResponse.builder()
                    .investType(investType)
                    .periods(periodOptions)
                    .build());
        }

        return options;
    }

    /***
     * 함수 기능: 전종목 x 전투자성향 x 지원되는 기간 프리셋에 대해 백테스트를 재계산하여 DB에 적재(upsert)한다.
     * 매일 새벽 스케줄러에서 호출된다.
     */
    public void recalculateAll() {

        List<Stock> stocks = stockRepository.findAll();
        int successCount = 0;
        int skipCount = 0;

        for (Stock stock : stocks) {
            LocalDate latestDate = dailyPriceRepository.findMaxDateByStock(stock);
            if (latestDate == null) {
                log.info("[백테스트 프리셋 배치] 일봉 데이터가 없어 스킵. 종목명: {}", stock.getStockName());
                continue;
            }

            for (int investType = 1; investType <= 5; investType++) {
                for (BacktestPeriod period : BacktestPeriod.values()) {
                    if (!isSupported(investType, period)) {
                        continue;
                    }

                    try {
                        recalculateOne(stock, investType, period, latestDate);
                        successCount++;
                    } catch (BacktestException e) {
                        // 데이터 기간이 부족한 종목 등은 스킵하고 다음으로 진행
                        log.warn("[백테스트 프리셋 배치] 스킵. 종목명: {}, 투자성향: {}, 기간: {}, 사유: {}",
                                stock.getStockName(), investType, period, e.getMessage());
                        skipCount++;
                    } catch (Exception e) {
                        log.error("[백테스트 프리셋 배치] 계산 실패. 종목명: {}, 투자성향: {}, 기간: {}",
                                stock.getStockName(), investType, period, e);
                        skipCount++;
                    }
                }
            }
        }

        log.info("[백테스트 프리셋 배치] 완료. 성공: {}건, 스킵: {}건", successCount, skipCount);
    }

    private void recalculateOne(Stock stock, int investType, BacktestPeriod period, LocalDate latestDate) {

        LocalDate startDate = period.calculateStartDate(latestDate);

        BacktestResDto.QuantScoringResponse result =
                backtestService.runStrategy(stock.getStockCode(), investType, startDate, latestDate);

        BacktestPresetResult entity = presetRepository
                .findByStock_StockCodeAndInvestTypeAndPeriod(stock.getStockCode(), investType, period)
                .map(existing -> existing.update(startDate, latestDate, result))
                .orElseGet(() -> BacktestPresetResult.create(stock, investType, period, startDate, latestDate, result));

        presetRepository.save(entity);
    }
}
