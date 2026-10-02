package juby.invest.domain.backtest.repository;

import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.entity.BacktestPresetResult;
import juby.invest.domain.backtest.enums.BacktestPeriod;
import juby.invest.domain.stock.entity.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대장주 프리셋 벌크 조회 쿼리를 검증한다.
 * <p>
 * 컨텍스트 로딩 테스트는 @Query의 문법만 확인하고 파라미터 바인딩은 확인하지 않는다.
 * 실제로 ":stockCodes" 앞에 공백이 섞여 있어도 하이버네이트는 파싱에 성공하지만,
 * 스프링 데이터가 바인딩 대상을 찾지 못해 호출 시점에야
 * "No argument for named parameter" 로 터졌다. 그래서 메서드를 직접 호출한다.
 */
@DataJpaTest
@DisplayName("BacktestPresetResult 대장주 프리셋 조회")
class BacktestPresetResultRepositoryTest {

    private static final int SMA_INVEST_TYPE = 3;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private BacktestPresetResultRepository presetRepository;

    @BeforeEach
    void setUp() {
        Stock skHynix = persistStock("000660", "SK하이닉스");
        Stock hanwha = persistStock("012450", "한화에어로스페이스");
        Stock samsungBio = persistStock("207940", "삼성바이오로직스");
        Stock samsung = persistStock("005930", "삼성전자");

        persistPreset(skHynix, SMA_INVEST_TYPE, BacktestPeriod.ONE_YEAR, new BigDecimal("0.345"));
        persistPreset(hanwha, SMA_INVEST_TYPE, BacktestPeriod.ONE_YEAR, new BigDecimal("0.128"));
        persistPreset(samsungBio, SMA_INVEST_TYPE, BacktestPeriod.ONE_YEAR, new BigDecimal("-0.052"));

        // 걸러져야 하는 행: 대상이 아닌 종목 / 다른 기간 / 다른 투자성향
        persistPreset(samsung, SMA_INVEST_TYPE, BacktestPeriod.ONE_YEAR, new BigDecimal("0.999"));
        persistPreset(skHynix, SMA_INVEST_TYPE, BacktestPeriod.SIX_MONTHS, new BigDecimal("0.111"));
        persistPreset(skHynix, 4, BacktestPeriod.ONE_YEAR, new BigDecimal("0.222"));

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("종목코드 목록으로 해당 투자성향/기간의 프리셋만 조회한다")
    void findsOnlyRequestedStocksAndPreset() {
        List<String> stockCodes = List.of("000660", "012450", "207940");

        List<BacktestPresetResult> result = presetRepository
                .findAllByStockStockCodesAndInvestTypeAndPeriod(stockCodes, SMA_INVEST_TYPE, BacktestPeriod.ONE_YEAR);

        assertThat(result).hasSize(3)
                .allSatisfy(preset -> {
                    assertThat(preset.getInvestType()).isEqualTo(SMA_INVEST_TYPE);
                    assertThat(preset.getPeriod()).isEqualTo(BacktestPeriod.ONE_YEAR);
                })
                .extracting(preset -> preset.getStock().getStockCode())
                .containsExactlyInAnyOrder("000660", "012450", "207940");
    }

    @Test
    @DisplayName("join fetch로 Stock을 함께 적재해 종목명 접근 시 추가 조회가 없다")
    void fetchesStockEagerly() {
        List<BacktestPresetResult> result = presetRepository
                .findAllByStockStockCodesAndInvestTypeAndPeriod(List.of("000660"), SMA_INVEST_TYPE, BacktestPeriod.ONE_YEAR);

        Stock stock = result.get(0).getStock();

        assertThat(em.getEntityManager().getEntityManagerFactory()
                .getPersistenceUnitUtil().isLoaded(stock)).isTrue();
        assertThat(stock.getStockName()).isEqualTo("SK하이닉스");
    }

    @Test
    @DisplayName("적재된 프리셋이 없으면 빈 목록을 반환한다")
    void returnsEmptyWhenNoPreset() {
        List<BacktestPresetResult> result = presetRepository
                .findAllByStockStockCodesAndInvestTypeAndPeriod(List.of("000660"), SMA_INVEST_TYPE, BacktestPeriod.ONE_MONTH);

        assertThat(result).isEmpty();
    }

    private Stock persistStock(String stockCode, String stockName) {
        Stock stock = Stock.builder().stockCode(stockCode).stockName(stockName).build();
        em.persist(stock);
        return stock;
    }

    private void persistPreset(Stock stock, int investType, BacktestPeriod period, BigDecimal totalReturn) {
        LocalDate endDate = LocalDate.of(2026, 9, 1);

        BacktestResDto.QuantScoringResponse result = BacktestResDto.QuantScoringResponse.builder()
                .stockCode(stock.getStockCode())
                .investType(investType)
                .finalScore(70.0)
                .stable(BacktestResDto.QuantScoringResponse.Stable.builder()
                        .mdd(BigDecimal.ZERO).volatility(BigDecimal.ZERO).dVolatility(BigDecimal.ZERO).build())
                .profit(BacktestResDto.QuantScoringResponse.Profit.builder()
                        .totalReturn(totalReturn).annualReturn(BigDecimal.ZERO).avgTradeReturn(BigDecimal.ZERO).build())
                .effect(BacktestResDto.QuantScoringResponse.Effect.builder()
                        .sharpeRatio(BigDecimal.ZERO).sortinoRatio(BigDecimal.ZERO).calmarRatio(BigDecimal.ZERO).build())
                .growth(BacktestResDto.QuantScoringResponse.Growth.builder()
                        .momentumRatio(BigDecimal.ZERO).volGrowthRatio(BigDecimal.ZERO).positionCount(4).build())
                .build();

        em.persist(BacktestPresetResult.create(stock, investType, period, period.calculateStartDate(endDate), endDate, result));
    }
}