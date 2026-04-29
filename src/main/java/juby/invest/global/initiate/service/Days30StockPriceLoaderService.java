package juby.invest.global.initiate.service;

import jakarta.transaction.Transactional;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.global.kisapi.market.dto.DailyStockPriceDto;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class Days30StockPriceLoaderService {

    private final RestClient investRestClient;
    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${kis.mock.app-key}") String appKey;
    @Value("${kis.mock.app-secret}") String appSecret;

    /***
     * 함수 기능: 주식일자별 현재가 API(30일) 호출한다.
     */
    private List<DailyStockPriceDto.Output> getDailyStockPrice(String stockCode, String accessToken){

        DailyStockPriceDto response = investRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_PERIOD_DIV_CODE", "D")
                        .queryParam("FID_ORG_ADJ_PRC", "1")
                        .build())
                .header("content-type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST01010400")
                .retrieve()
                .body(DailyStockPriceDto.class);

        if (response != null && response.rtCd().equals("0")){
            log.info("API 호출 성공");
        }
        else{
            log.error("API 호출 실패 : {}", response == null ? "null" : response.message());
        }

        return response.output();
    }

    /***
     * 함수: 단일종목의 30일 주식 정보들 받아와, DB에 저장한다.
     * @param stockCode 주식코드 6자리
     */
    @Transactional
    public void saveDailyStockPrice(String stockCode, String accessToken) {

        Stock stock = stockRepository.findById(stockCode)
                .orElseThrow(() -> new RuntimeException("주식을 찾을 수 없습니다."));

        List<DailyStockPriceDto.Output> dailyStockPrice = getDailyStockPrice(stockCode, accessToken);

        List<DailyPrice> list = dailyStockPrice.stream().map(
                o -> DailyPrice.builder()
                        .stock(stock)
                        .date(LocalDate.parse(o.date(), formatter))
                        .openPrice(Integer.parseInt(o.openPrice()))
                        .highPrice(Integer.parseInt(o.highPrice()))
                        .lowPrice(Integer.parseInt(o.lowPrice()))
                        .closePrice(Integer.parseInt(o.closePrice()))
                        .volume(Integer.parseInt(o.volume()))
                        .build()).toList();

        dailyPriceRepository.saveAll(list);
        log.info("종목 저장 완료: {}", stockCode);
    }
}
