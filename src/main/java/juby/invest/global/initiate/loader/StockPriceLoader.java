package juby.invest.global.initiate.loader;

import juby.invest.global.kisapi.market.dto.PeriodStockPriceDto;
import juby.invest.global.initiate.api.KsiApiClient;
import juby.invest.global.initiate.service.PeriodStockPriceLoaderService;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockPriceLoader {

    private final StockRepository stockRepository;
    private final KsiApiClient ksiApiClient;
    private final PeriodStockPriceLoaderService periodStockPriceLoaderService;
    private static final List<String> dateStartList = List.of("20250101", "20250401", "20250701", "20251001", "20260101");
    private static final List<String> dateEndList = List.of("20250331", "20250630", "20250930", "20251231", "20260228");

    /***
     * 함수 기능: top100개 종목의 기간별 정보들을 조회하여 DB에 저장한다.
     */
    public void getPeriodByStockOHLVC(){
        List<String> stockCodesList = stockRepository.findAllStockCodes();

        for (String stockCode : stockCodesList){
            List<PeriodStockPriceDto.Output> combinedOutputList = new ArrayList<>();

            for (int i = 0; i < dateStartList.size(); i++){
                try {
                    List<PeriodStockPriceDto.Output> outputList = ksiApiClient.getPeriodStockPrice(stockCode, dateStartList.get(i), dateEndList.get(i));
                    combinedOutputList.addAll(outputList);
                    Thread.sleep(1200);
                } catch (Exception e){
                    log.error("종목 가져오기 실패 : {}", e.getMessage());
                }
            }

            if (!combinedOutputList.isEmpty()){
                periodStockPriceLoaderService.savePeriodStockPrice(stockCode, combinedOutputList);
            }
        }
        log.info("모든 종목 INSERT 완료");
    }
}
