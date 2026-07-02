package juby.invest.domain.backtest.indicator;

import juby.invest.domain.backtest.dto.BacktestResDto;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;

@Component
public class StableIndicator {

    public BacktestResDto.QuantScoringResponse.Stable indicate(BarSeries series, TradingRecord record){

        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();


        return BacktestResDto.QuantScoringResponse.Stable.builder()
                .dVolatility()
                .mdd(mdd.calculate(series, record))
                .volatility()
                .build();
    }
}
