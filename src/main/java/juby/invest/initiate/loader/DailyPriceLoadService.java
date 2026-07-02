package juby.invest.initiate.loader;

import juby.invest.domain.kis.market.dto.PeriodDailyPriceDto;
import juby.invest.domain.kis.market.service.MarketService;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyPriceLoadService {

    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final MarketService marketService;
    private static final LocalDate LOAD_START_DATE = LocalDate.of(2024, 10, 1);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    private List<String[]> generateDateRanges(LocalDate startDate) {
        List<String[]> ranges = new ArrayList<>();
        LocalDate current = startDate;
        LocalDate today = LocalDate.now();

        while (current.isBefore(today)) {
            LocalDate chunkEnd = current.plusMonths(3).minusDays(1);
            if (chunkEnd.isAfter(today)) {
                chunkEnd = today;
            }
            ranges.add(new String[]{current.format(formatter), chunkEnd.format(formatter)});
            current = chunkEnd.plusDays(1);
        }

        return ranges;
    }

    /***
     * 함수 기능: top100개 종목의 기간별 정보들을 조회하여 DB에 저장한다.
     */
    public void getPeriodByStockOHLVC(){
        List<Stock> stockList = stockRepository.findAll();

        for (Stock stock : stockList){

            LocalDate lastDate = dailyPriceRepository.findMaxDateByStock(stock);
            LocalDate startDate = (lastDate != null) ? lastDate.plusDays(1) : LOAD_START_DATE;

            if (!startDate.isBefore(LocalDate.now())) {
                log.info("종목명: {} - 이미 최신 데이터까지 적재 완료.", stock.getStockName());
                continue;
            }

            List<String[]> stockDateRanges = generateDateRanges(startDate);
            List<PeriodDailyPriceDto.Output> totalPeriodDailyPrice = new ArrayList<>();

            for (String[] range : stockDateRanges){
                try {
                    List<PeriodDailyPriceDto.Output> periodDailyPrice = marketService.getPeriodStockPrice(stock.getStockCode(), range[0], range[1]);
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
                    log.info("종목명: {} 2024-10-01 ~ 오늘까지 일봉 저장 완료", stock.getStockName());

                } catch (Exception e){
                    log.error("OHLVC 데이터를 DB 저장 중 오류 발생", e);
                    throw new RuntimeException("오류 발생");
                }
            }
        }
        log.info("모든 종목 INSERT 완료");
    }
}
