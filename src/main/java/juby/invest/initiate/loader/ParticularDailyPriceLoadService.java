package juby.invest.initiate.loader;

import juby.invest.domain.kis.market.dto.PeriodDailyPriceDto;
import juby.invest.domain.kis.market.service.MarketService;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import juby.invest.global.apiPayload.code.GeneralErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ParticularDailyPriceLoadService {

    private final MarketService marketService;
    private final DailyPriceRepository dailyPriceRepository;
    private final StockRepository stockRepository;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void getDailyPrice(String date) throws InterruptedException {

        log.info("모든 종목의 특정 날짜 OHLVC 데이터를 DB에 삽입하는 작업 시작");

        List<Stock> stocks = stockRepository.findAll();

        for (Stock stock : stocks){
            try {
                List<PeriodDailyPriceDto.Output> response = marketService.getPeriodStockPrice(stock.getStockCode(), date, date);

                dailyPriceRepository.save(DailyPrice.builder()
                                .stock(stock)
                                .date(LocalDate.parse(date, dateTimeFormatter))
                                .openPrice(Integer.parseInt(response.openPrice()))
                                .highPrice(Integer.parseInt(response.highPrice()))
                                .lowPrice(Integer.parseInt(response.lowPrice()))
                                .closePrice(Integer.parseInt(response.closePrice()))
                                .volume(Integer.parseInt(response.volume()))
                                .build());
                log.info("{}: 삽입 완료", stock.getStockName());
            } catch (Exception e){
                log.info("{}: 삽입 실패", stock.getStockName());
                throw new ProjectException(GeneralErrorCode.BAD_REQUEST);
            }


            Thread.sleep(1200);
        }
    }
}
