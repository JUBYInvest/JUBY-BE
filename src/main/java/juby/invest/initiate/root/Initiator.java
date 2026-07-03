package juby.invest.initiate.root;

import juby.invest.initiate.loader.StockLoadService;
import juby.invest.initiate.loader.DailyPriceLoadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class Initiator {

    private final StockLoadService stockLoadService;
    private final DailyPriceLoadService dailyPriceLoadService;

//    @EventListener(ApplicationReadyEvent.class)
    public void initiate(){

        try {
            stockLoadService.initStockCodeAndStockName(); // 시가총액 TOP 100개 종목코드와 종목명 INSERT 쿼리 (3/12 기준)
            dailyPriceLoadService.getPeriodByStockOHLVC(); // 종목별 일자, OHLVC 정보 INSERT 쿼리 (2025년, 2026년 1분기)
        } catch (Exception e){
            throw new RuntimeException("initiate 과정 중 문제 발생", e);
        }

        log.info("initiate 과정 완료 (DB에 초기 정보 INSERT)");
    }
}
