package juby.invest.global.scheduler;

import juby.invest.domain.kis.market.dto.HolidayDto;
import juby.invest.domain.kis.market.dto.PeriodDailyPriceDto;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@Profile("dev") // EC2에서만 실행
public class DailyPriceScheduler {

    private final MarketService marketService;
    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    /***
     * 스케줄러 동작 시각: 월~금 16시(본 실행), 16시 30분 / 17시 (누락 종목 보정 실행)
     * 수행 동작: 100개 종목의 금일 종가 데이터를 DB에 저장한다.
     *          이미 수집된 종목은 existsByStockAndDate로 스킵하므로 보정 실행은 누락분만 채운다.
     * 참고: cron zone 미지정 -> JVM 기본 시간대 (Dockerfile의 -Duser.timezone) 사용
     */
    @Scheduled(cron = "0 0,30 16 * * MON-FRI")
    @Scheduled(cron = "0 0 17 * * MON-FRI")
    public void getDailyPrice() throws InterruptedException {

        // 장날 or 휴장일인지 먼저 파악. 휴장일이면 스케줄러 동작 x
        LocalDate today = LocalDate.now();
        String todayString = dateTimeFormatter.format(today);

        List<HolidayDto.Output> holidayList = marketService.getHolidayList(todayString);

        boolean isClosed = holidayList.stream()
                .filter(day -> day.baseDate().equals(todayString))
                .findFirst()
                .map(day -> "N".equals(day.openDay()))
                .orElse(false);

        if (isClosed){
            log.info("[스케줄러-1] 금일({})은 휴장일이어서 스케줄러 동작 x", today);
            return;
        }

        // 일봉 수집 스케줄러 동작 시작
        log.info("[스케줄러-1] 일봉 수집 스케줄러 동작 시작.");
        List<Stock> stocks = stockRepository.findAll();
        List<DailyPrice> dailyPrices = new ArrayList<>(); // DB에 Batch 단위로 집어넣기 위해 선언

        int failedCnt = 0;
        for (Stock stock : stocks) {

            // 이미 수집된 종목의 현재가 시세인 경우 스킵한다.
            if (dailyPriceRepository.existsByStockAndDate(stock, today)){
                log.info("이미 해당 종목의 데이터가 DB에 존재하므로 시세 조회를 건너뜁니다. 종목코드: {}, 날짜: {}", stock.getStockCode(), today);
                continue;
            }

            try {
                PeriodDailyPriceDto.Output dailyPrice = marketService.getPeriodStockPrice(stock.getStockCode(), todayString, todayString).getFirst();

                dailyPrices.add(DailyPrice.builder()
                        .stock(stock)
                        .date(today)
                        .openPrice(Integer.parseInt(dailyPrice.openPrice()))
                        .highPrice(Integer.parseInt(dailyPrice.highPrice()))
                        .lowPrice(Integer.parseInt(dailyPrice.lowPrice()))
                        .closePrice(Integer.parseInt(dailyPrice.closePrice()))
                        .volume(Integer.parseInt(dailyPrice.volume()))
                        .tradingValue(Long.parseLong(dailyPrice.tradingValue()))
                        .build()
                );

                log.info("종목명: {}, 일봉 데이터 스케줄러 동작 완료.", stock.getStockName());
            } catch (Exception e) {
                failedCnt++;
                log.warn("스케줄러 동작 중 문제 발생: {}, 종목명: {}", e, stock.getStockName());
            }
            Thread.sleep(1200); // 모의 도메인 API 호출 제한: 1초당 1건
        }

        dailyPriceRepository.saveAll(dailyPrices); // 정상 시행 시, 총 102개 종목 삽입
        log.info("[스케줄러-1] 일봉 수집 스케줄러 동작 완료. 총 {}개 삽입, {}개 실패", dailyPrices.size(), failedCnt);
    }
}
