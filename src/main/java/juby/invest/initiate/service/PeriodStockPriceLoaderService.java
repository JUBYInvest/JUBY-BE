package juby.invest.initiate.service;

import jakarta.transaction.Transactional;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.global.kisapi.market.dto.PeriodStockPriceDto;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PeriodStockPriceLoaderService {

    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    /***
     * 함수 기능: 종목의 날짜별 데이터를 DB에 INSERT
     * @param stockCode 주식 코드 (6자리)
     * @param outputList API 응답 리스트 (날짜별로 존재)
     */
    @Transactional
    public void savePeriodStockPrice(String stockCode, List<PeriodStockPriceDto.Output> outputList){
        Stock stock = stockRepository.findById(stockCode)
                .orElseThrow(() -> new RuntimeException("주식을 찾을 수 없습니다."));

        // 이미 DB에 값이 저장되어 있다면 return;
        if (dailyPriceRepository.existsByStock(stock)){
            log.info("해당 주식에 대한 데이터가 이미 존재합니다.");
            return;
        }

        List<DailyPrice> list = outputList.stream().map(
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
        log.info("종목 저장 완료 {} {}", stockCode, stock.getStockName());
    }
}
