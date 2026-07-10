package juby.invest.domain.kis.market.scheduler;

import io.netty.handler.codec.DateFormatter;
import juby.invest.domain.kis.market.dto.DailyPriceDto;
import juby.invest.domain.kis.market.dto.HolidayDto;
import juby.invest.domain.kis.market.service.MarketService;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@Profile("dev") // EC2에서만 실행
public class DailyPriceScheduler {

    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final MarketService marketService;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Scheduled(cron = "0 10 22 * * MON-FRI", zone = "Asia/Seoul")
    public void getDailyPrice() throws InterruptedException {

        // 장날/휴장일인지 먼저 파악
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String todayString = dateTimeFormatter.format(today);

        List<HolidayDto.Output> holidayList = marketService.getHolidayList(todayString);
        HolidayDto.Output first = holidayList.getFirst();
        if (first.baseDate().equals(todayString) && first.openDay().equals("N")){
            log.info("[스케줄러-1] 금일({})은 휴장일이어서 스케줄러 동작 x", today);
            return;
        }

        // 스케줄러 동작 시작
        log.info("[스케줄러-1] 일봉 수집 스케줄러 동작 시작.");
        List<Stock> stocks = stockRepository.findAll();
        List<DailyPrice> dailyPrices = new ArrayList<>();

        for (Stock stock : stocks) {
            if (dailyPriceRepository.existsByStock_StockCodeAndDate(stock.getStockCode(), today)){
                log.info("이미 해당 종목의 종가가 DB에 존재합니다. 종목코드: {}, 날짜: {}", stock.getStockCode(), today);
                continue;
            }

            try {
                DailyPriceDto.Output dailyPrice = marketService.getDailyPrice(stock.getStockCode());

                dailyPrices.add(DailyPrice.builder()
                        .stock(stock)
                        .date(today)
                        .openPrice(Integer.parseInt(dailyPrice.openPrice()))
                        .highPrice(Integer.parseInt(dailyPrice.highPrice()))
                        .lowPrice(Integer.parseInt(dailyPrice.lowPrice()))
                        .closePrice(Integer.parseInt(dailyPrice.currentPrice()))
                        .volume(Integer.parseInt(dailyPrice.volume()))
                        .build()
                );

                log.info("종목명: {}, 일봉 데이터 스케줄러 동작 완료.", stock.getStockName());
            } catch (Exception e) {
                log.error("스케줄러 동작 중 문제 발생: {}, 종목명: {}", e, stock.getStockName());
            }
            Thread.sleep(500); // 실전 도메인 API 호출 제한: 1초당 18건
        }

        if (!dailyPrices.isEmpty()){
            dailyPriceRepository.saveAll(dailyPrices);
            log.info("[스케줄러-1] 일봉 수집 스케줄러 동작 완료. 총 {}개 삽입", dailyPrices.size());
        }
    }
}
