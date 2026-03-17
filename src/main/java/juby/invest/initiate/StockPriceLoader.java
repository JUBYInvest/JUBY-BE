package juby.invest.initiate;

import juby.invest.dto.PeriodStockPriceDto;
import juby.invest.repository.StockRepository;
import juby.invest.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockPriceLoader {

    private final StockRepository stockRepository;
    private final Days30StockPriceLoaderService days30StockPriceLoaderService;
    private final KsiApiClient ksiApiClient;
    private final PeriodStockPriceLoaderService periodStockPriceLoaderService;
    private final TokenService tokenService ;

    /***
     * 함수 기능: top 100 종목의 리스트를 가져와, 각 종목에 대한 정보(30일)를 받아온다. (initate용)
     */
    //@EventListener(ApplicationReadyEvent.class)
    public void getListOfTop100StockCodes(){
        List<String> stockCodesList = stockRepository.findAllStockCodes();

        // mockAccessToken 가져오기 호출
        String accessToken = tokenService.getMockAccessToken().accessToken();

        for (String stockCode : stockCodesList){
            try {
                days30StockPriceLoaderService.saveDailyStockPrice(stockCode, accessToken);
                Thread.sleep(1200); // API 제한 준수
            } catch (Exception e) {
                log.error("종목 가져오기 실패: {}", e.getMessage());
            }
        }
    }

    /***
     * 함수 기능: top100개 종목의 기간별 정보들을 조회하여 DB에 저장한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void getListOfTop100StockCodesAndPeriod(){
        List<String> stockCodesList = stockRepository.findAllStockCodes();

        String accessToken = tokenService.getMockAccessToken().accessToken();

        for (String stockCode : stockCodesList){
            try {
                List<PeriodStockPriceDto.Output> outputList = ksiApiClient.getPeriodStockPrice(stockCode, "20260101", "20260126", accessToken);
                periodStockPriceLoaderService.savePeriodStockPrice(stockCode, outputList);
                Thread.sleep(1200);
            } catch (Exception e){
                log.error("종목 가져오기 실패 : {}", e.getMessage());
            }
        }
        log.info("모든 종목 INSERT 완료");
    }
}
