package juby.invest.initiate;

import jakarta.transaction.Transactional;
import juby.invest.domain.DailyPrice;
import juby.invest.domain.Stock;
import juby.invest.dto.PeriodStockPriceDto;
import juby.invest.repository.DailyPriceRepository;
import juby.invest.repository.StockRepository;
import juby.invest.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.swing.text.DateFormatter;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
     * 함수 기능: 종목의 날짜별 데이터를 DB에 저장하는 함수
     * @param stockCode 주식 코드 (6자리)
     * @param outputList API 응답 리스트 (날짜별로 존재)
     */
    @Transactional
    public void savePeriodStockPrice(String stockCode, List<PeriodStockPriceDto.Output> outputList){
        Stock stock = stockRepository.findById(stockCode)
                .orElseThrow(() -> new RuntimeException("주식을 찾을 수 없습니다."));

        // 이미 DB에 값이 저장되어 있다면 return;
//        if (dailyPriceRepository.existsByStockAndDate(stock, LocalDate.of(2025,2,3))){
//            return;
//        }

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
        log.info("종목 저장 완료 {}", stockCode);
    }
}
