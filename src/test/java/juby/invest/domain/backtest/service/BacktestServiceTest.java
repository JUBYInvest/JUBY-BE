package juby.invest.domain.backtest.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import juby.invest.domain.backtest.converter.AnalysisCriterionConverter;
import juby.invest.domain.backtest.converter.BarSeriesConverter;
import juby.invest.domain.backtest.converter.ScoreCalculator;
import juby.invest.domain.backtest.indicator.EffectIndicator;
import juby.invest.domain.backtest.indicator.GrowthIndicator;
import juby.invest.domain.backtest.indicator.ProfitIndicator;
import juby.invest.domain.backtest.indicator.StableIndicator;
import juby.invest.domain.backtest.strategy.BacktestStrategy;
import juby.invest.domain.backtest.strategy.RsiReversionStrategy;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("BacktestService 체결 내역 로그")
class BacktestServiceTest {

    private static final Stock STOCK = Stock.builder().stockCode("005930").stockName("삼성전자").build();
    private static final LocalDate START_DATE = LocalDate.of(2026, 4, 28);

    private DailyPriceRepository dailyPriceRepository;
    private BacktestService backtestService;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        dailyPriceRepository = mock(DailyPriceRepository.class);

        AnalysisCriterionConverter analysisCriterionConverter = new AnalysisCriterionConverter(
                new EffectIndicator(), new GrowthIndicator(), new ProfitIndicator(),
                new StableIndicator(), new ScoreCalculator());

        Map<String, BacktestStrategy> strategyMap =
                Map.of("rsiReversionStrategy", new RsiReversionStrategy());

        backtestService = new BacktestService(
                new BarSeriesConverter(), dailyPriceRepository, strategyMap, analysisCriterionConverter);

        logs = new ListAppender<>();
        logs.start();
        logger = (Logger) LoggerFactory.getLogger(BacktestService.class);
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logs.stop();
    }

    private static DailyPrice price(LocalDate date, int close) {
        return DailyPrice.builder()
                .date(date)
                .openPrice(close).highPrice(close).lowPrice(close).closePrice(close)
                .volume(1_000_000)
                .build();
    }

    /***
     * 워밍업 150봉은 소폭 등락(RSI를 50 근처에 둔다), 요청 구간은 계속 하락시킨다.
     * -> 요청 구간 초반에 RSI가 40을 하향 돌파해 매수가 잡히고, 70을 다시 넘지 못해 끝까지 미청산으로 남는다.
     */
    private void givenDecliningPrices() {
        List<DailyPrice> warmup = new ArrayList<>();
        int close = 50_000;
        LocalDate date = START_DATE.minusDays(150);

        for (int i = 0; i < 150; i++) {
            close += (i % 2 == 0) ? 300 : -250;
            warmup.add(price(date, close));
            date = date.plusDays(1);
        }

        List<DailyPrice> requested = new ArrayList<>();
        date = START_DATE;

        for (int i = 0; i < 30; i++) {
            close -= 800;
            requested.add(price(date, close));
            date = date.plusDays(1);
        }

        // 리포지토리는 최신순으로 돌려주므로 날짜 내림차순으로 뒤집어 준다.
        List<DailyPrice> warmupDesc = new ArrayList<>(warmup);
        warmupDesc.sort(Comparator.comparing(DailyPrice::getDate).reversed());

        given(dailyPriceRepository.findByStockAndDateBetweenOrderByDateAsc(eq(STOCK), any(), any()))
                .willReturn(requested);
        given(dailyPriceRepository.findByStockAndDateLessThanOrderByDateDesc(eq(STOCK), eq(START_DATE), any(Limit.class)))
                .willReturn(warmupDesc);
    }

    private List<String> messages() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    @DisplayName("매도 신호가 없어 청산되지 않은 매수도 로그로 남긴다")
    void logsOpenPosition() {
        givenDecliningPrices();

        backtestService.runStrategy(STOCK, 1, START_DATE, START_DATE.plusDays(29));

        assertThat(messages())
                .anySatisfy(message -> assertThat(message).startsWith("체결된 포지션 개수: 0"))
                .anySatisfy(message -> assertThat(message)
                        .startsWith("미청산 내역 - 매수 ")
                        .contains("매도 신호 없음")
                        .contains("평가수익률"));
    }

    @Test
    @DisplayName("미청산 내역은 청산된 체결 내역과 따로 찍힌다")
    void doesNotReportOpenPositionAsClosedTrade() {
        givenDecliningPrices();

        backtestService.runStrategy(STOCK, 1, START_DATE, START_DATE.plusDays(29));

        assertThat(messages()).noneSatisfy(message -> assertThat(message).startsWith("체결 내역 - "));
    }
}
