package juby.invest.ta4j.converter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.SharpeRatioCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.helpers.StandardDeviationCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;

import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
public class AnalysisCriterionConverter {

    public List<BigDecimal> converter(BarSeries series, TradingRecord record){
        BigDecimal totalReturn = BigDecimal.valueOf(new NetReturnCriterion().calculate(series, record).doubleValue());
        // 연평균 수익률 추후 추가
        BigDecimal sharpeRatio = BigDecimal.valueOf(new SharpeRatioCriterion().calculate(series, record).doubleValue());
        BigDecimal stdDeviation = BigDecimal.valueOf(new StandardDeviationCriterion(new NetReturnCriterion()).calculate(series, record).doubleValue());
        BigDecimal maxDrawdown = BigDecimal.valueOf(new MaximumDrawdownCriterion().calculate(series, record).doubleValue());

        return List.of(totalReturn, totalReturn, sharpeRatio, stdDeviation, maxDrawdown);
    }
}
