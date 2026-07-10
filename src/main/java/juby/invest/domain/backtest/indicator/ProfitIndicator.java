package juby.invest.domain.backtest.indicator;

import juby.invest.domain.backtest.dto.BacktestResDto;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ProfitIndicator {

    /***
     *  수익성 축
     *  누적수익률, CAGR(연평균 수익률), 평균 거래 수익률
     */
    public BacktestResDto.QuantScoringResponse.Profit indicate(BarSeries series, TradingRecord record){

        // 누적수익률
        Num totalReturn = new NetReturnCriterion().calculate(series, record).minus(series.numFactory().numOf(1));

        // CAGR
        Double cagr = calculateAnnualReturn(series, record, totalReturn.doubleValue());

        // 평균 거래 수익률
        Num avgTradeReturn = calculateAvgTradeReturn(series, record);

        return BacktestResDto.QuantScoringResponse.Profit.builder()
                .totalReturn(totalReturn.bigDecimalValue())
                .annualReturn(BigDecimal.valueOf(cagr))
                .avgTradeReturn(avgTradeReturn.bigDecimalValue())
                .build();
    }

    /***
     *  연환산 수익률 = (1 + 누적 수익률)^(252/계산 일수) - 1
     *  복리 방식 (수익금이 다시 재투자된다.)
     *  252 = 주식 시장 개장 총일
     */
    private Double calculateAnnualReturn(BarSeries series, TradingRecord record, Double totalReturn){

        int barCount = series.getBarCount();

        return Math.pow(1 + totalReturn, 252.0 / barCount) - 1;
    }

    /***
     * 평균 거래 수익률 = 거래 1회당 평균 수익률
     * 각 거래 횟수 마다의 (매도가 - 매수가)/매수가 -> 거래 1회당 수익률
     * 을 모두 더하고 거래 횟수로 나눈다.
     */
    private Num calculateAvgTradeReturn(BarSeries series, TradingRecord record){

        // 총 포지션(거래 횟수)
        List<Position> positions = record.getPositions();

        // 포지션이 없다면 평균 거래 수익률 0 반환
        if (positions.isEmpty()){
            return series.numFactory().numOf(0);
        }

        Num totalReturnSum = series.numFactory().numOf(0);

        // 각 포지션 당 거래 수익률 계산 후 누적합
        for (Position position : positions) {
            Num entryPrice = position.getEntry().getNetPrice();
            Num exitPrice = position.getExit().getNetPrice();

            Num tradeReturn = exitPrice.minus(entryPrice).dividedBy(entryPrice);

            totalReturnSum = totalReturnSum.plus(tradeReturn);
        }

        // 총 거래 수익률을 N(거래 횟수)으로 나눈다.
        Num totalTrades = series.numFactory().numOf(positions.size());
        return totalReturnSum.dividedBy(totalTrades);
    }
}
