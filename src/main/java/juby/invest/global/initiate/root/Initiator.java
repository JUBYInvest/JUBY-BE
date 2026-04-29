package juby.invest.global.initiate.root;

import juby.invest.global.initiate.loader.StockLoader;
import juby.invest.global.initiate.loader.StockPriceLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class Initiator {

    private final StockLoader stockLoader;
    private final StockPriceLoader stockPriceLoader;

    // @EventListener(ApplicationReadyEvent.class)
    public void initiate(){

        try {
            stockLoader.initStockCodeAndStockName(); // 시가총액 TOP 100개 종목코드와 종목명 INSERT 쿼리 (3/12 기준)
            stockPriceLoader.getPeriodByStockOHLVC(); // 종목별 일자, OHLVC 정보 INSERT 쿼리 (2025년, 2026년 1분기)
        } catch (Exception e){
            throw new RuntimeException("initiate 과정 중 문제 발생", e);
        }

        log.info("initiate 과정 완료 (DB에 초기 정보 INSERT)");
    }
}
