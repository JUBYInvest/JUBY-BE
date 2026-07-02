package juby.invest.domain.backtest.indicator;

import juby.invest.domain.backtest.dto.BacktestResDto;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;

@Component
public class ProfitIndicator {

    public BacktestResDto.QuantScoringResponse.Profit indicate(BarSeries series, TradingRecord record){

        return null;
    }
}
