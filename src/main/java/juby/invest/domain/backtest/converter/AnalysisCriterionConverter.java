package juby.invest.domain.backtest.converter;

import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.indicator.EfficiencyIndicator;
import juby.invest.domain.backtest.indicator.GrowthIndicator;
import juby.invest.domain.backtest.indicator.ProfitIndicator;
import juby.invest.domain.backtest.indicator.StableIndicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.SharpeRatioCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.helpers.StandardDeviationCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class AnalysisCriterionConverter {

    private final EfficiencyIndicator efficiencyIndicator;
    private final GrowthIndicator growthIndicator;
    private final ProfitIndicator profitIndicator;
    private final StableIndicator stableIndicator;

    public BacktestResDto.QuantScoringResponse converter(
            String stockCode, int investType, BarSeries series, TradingRecord record){

        BacktestResDto.QuantScoringResponse.Stable stable = stableIndicator.indicate(series, record);
        BacktestResDto.QuantScoringResponse.Profit profit = profitIndicator.indicate(series, record);
        BacktestResDto.QuantScoringResponse.Effect effect = efficiencyIndicator.indicate(series, record);
        BacktestResDto.QuantScoringResponse.Growth growth = growthIndicator.indicate(series, record);

        return BacktestResDto.QuantScoringResponse.builder()
                .stockCode(stockCode)
                .investType(investType)
                .stable(stable)
                .growth(growth)
                .effect(effect)
                .profit(profit)
                .build();
    }
}
