package juby.invest.ta4j.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Strategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class StrategyFactory {

    // 원하는 전략을 뽑아주는 공장 같은 느낌
    private static final Map<Integer, BacktestStrategy> strategyMap = new HashMap<>();
    private static int index = 0;

    /***
     * 함수: 스프링이 BacktestStrategy를 구현한 '모든 '빈'을 찯아 List에 담아준다.
     * @param strategyList 모든 전략 종류 (ex, SMA, EMA, RSI)
     */
    public StrategyFactory(List<BacktestStrategy> strategyList){
        for (BacktestStrategy strategy : strategyList) {
            strategyMap.put(++index, strategy);
            log.info("strategyMap에 {} 주입.", index);
        }
    }

    /***
     * 함수: 전달받은 전략 번호에 맞는 전략을 반환하는 함수.
     * @param strategyNum 전략 번호 (ex 1 = SMA, 2 = EMA)
     * @return Strategy
     */
    public BacktestStrategy getStrategy(int strategyNum){
        if (strategyMap.get(strategyNum) == null){
            throw new RuntimeException("해당 전략이 존재하지 않습니다. strategyNum = " +  strategyNum);
        }
        return strategyMap.get(strategyNum);
    }
}
