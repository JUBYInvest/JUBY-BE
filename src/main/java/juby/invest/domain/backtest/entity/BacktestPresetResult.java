package juby.invest.domain.backtest.entity;

import jakarta.persistence.*;
import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.enums.BacktestPeriod;
import juby.invest.domain.stock.entity.Stock;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "backtest_preset_result",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_backtest_preset_result",
                columnNames = {"stock_code", "invest_type", "period"}
        ))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BacktestPresetResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private Stock stock;

    @Column(name = "invest_type", nullable = false)
    private int investType;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 20)
    private BacktestPeriod period;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "final_score", nullable = false)
    private double finalScore;

    // 안정성
    private BigDecimal mdd;
    private BigDecimal volatility;
    private BigDecimal dVolatility;

    // 수익성
    private BigDecimal totalReturn;
    private BigDecimal annualReturn;
    private BigDecimal avgTradeReturn;

    // 효율성
    private BigDecimal sharpeRatio;
    private BigDecimal sortinoRatio;
    private BigDecimal calmarRatio;

    // 성장성
    private BigDecimal momentumRatio;
    private BigDecimal volGrowthRatio;
    private int positionCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private BacktestPresetResult(Stock stock, int investType, BacktestPeriod period) {
        this.stock = stock;
        this.investType = investType;
        this.period = period;
    }

    /***
     * 함수 기능: 새로운 프리셋 계산 결과 엔티티를 생성한다.
     */
    public static BacktestPresetResult create(
            Stock stock, int investType, BacktestPeriod period,
            LocalDate startDate, LocalDate endDate,
            BacktestResDto.QuantScoringResponse result
    ) {
        BacktestPresetResult entity = new BacktestPresetResult(stock, investType, period);
        entity.applyResult(startDate, endDate, result);
        return entity;
    }

    /***
     * 함수 기능: 기존 프리셋 계산 결과를 재계산된 값으로 갱신한다. (배치 재실행 시 upsert 용도)
     */
    public BacktestPresetResult update(LocalDate startDate, LocalDate endDate, BacktestResDto.QuantScoringResponse result) {
        applyResult(startDate, endDate, result);
        return this;
    }

    private void applyResult(LocalDate startDate, LocalDate endDate, BacktestResDto.QuantScoringResponse result) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.finalScore = result.finalScore();

        this.mdd = result.stable().mdd();
        this.volatility = result.stable().volatility();
        this.dVolatility = result.stable().dVolatility();

        this.totalReturn = result.profit().totalReturn();
        this.annualReturn = result.profit().annualReturn();
        this.avgTradeReturn = result.profit().avgTradeReturn();

        this.sharpeRatio = result.effect().sharpeRatio();
        this.sortinoRatio = result.effect().sortinoRatio();
        this.calmarRatio = result.effect().calmarRatio();

        this.momentumRatio = result.growth().momentumRatio();
        this.volGrowthRatio = result.growth().volGrowthRatio();
        this.positionCount = result.growth().positionCount();

        this.updatedAt = LocalDateTime.now();
    }
}
