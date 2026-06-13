package juby.invest.initiate.loader;

import io.netty.handler.codec.DateFormatter;
import juby.invest.domain.kis.market.dto.PeriodDailyPriceDto;
import juby.invest.domain.kis.market.service.MarketService;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyPriceLoadService {

    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final MarketService marketService;
    private static final List<String> dateStartList = List.of("20250101", "20250401", "20250701", "20251001", "20260101", "20260401");
    private static final List<String> dateEndList = List.of("20250331", "20250630", "20250930", "20251231", "20260331", "20260612");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    /***
     * 함수 기능: top100개 종목의 기간별 정보들을 조회하여 DB에 저장한다.
     */
    public void getPeriodByStockOHLVC(){
        List<Stock> stockList = stockRepository.findAll();

        for (Stock stock : stockList){

            if (!dailyPriceRepository.findAllByStock(stock).isEmpty()){
                log.info("이미 DB에 저장된 종목이어서 건너뜁니다.");
                continue;
            }

            List<PeriodDailyPriceDto.Output> totalPeriodDailyPrice = new ArrayList<>();

            for (int i = 0; i < dateStartList.size(); i++){
                try {
                    List<PeriodDailyPriceDto.Output> periodDailyPrice = marketService.getPeriodStockPrice(stock.getStockCode(), dateStartList.get(i), dateEndList.get(i));
                    totalPeriodDailyPrice.addAll(periodDailyPrice);
                    Thread.sleep(1200);
                } catch (Exception e){
                    log.error("종목 가져오기 실패 : {}", e.getMessage());
                }
            }

            // 전체 기간 OHLVC 데이터를 가져왔다면, DB에 저장
            if (!totalPeriodDailyPrice.isEmpty()){
                try {
                    List<DailyPrice> dailyPriceList = totalPeriodDailyPrice.stream().map(
                            data -> DailyPrice.builder()
                                    .stock(stock)
                                    .date(LocalDate.parse(data.date(), formatter))
                                    .openPrice(Integer.parseInt(data.openPrice()))
                                    .highPrice(Integer.parseInt(data.highPrice()))
                                    .lowPrice(Integer.parseInt(data.lowPrice()))
                                    .closePrice(Integer.parseInt(data.closePrice()))
                                    .volume(Integer.parseInt(data.volume()))
                                    .build()).toList();
                    dailyPriceRepository.saveAll(dailyPriceList);
                    log.info("종목명: {} 2025-01-01 ~ 2026-06-12 일봉 저장 완료", stock.getStockName());

                } catch (Exception e){
                    log.error("OHLVC 데이터를 DB 저장 중 오류 발생", e);
                    throw new RuntimeException("오류 발생");
                }
            }
        }
        log.info("모든 종목 INSERT 완료");
    }
}
