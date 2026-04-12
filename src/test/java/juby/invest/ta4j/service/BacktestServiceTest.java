package juby.invest.ta4j.service;

import jakarta.transaction.Transactional;
import juby.invest.domain.DailyPrice;
import juby.invest.domain.Stock;
import juby.invest.repository.DailyPriceRepository;
import juby.invest.repository.StockRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class BacktestServiceTest {

    @Autowired private BacktestService backtestService;
    @Autowired private DailyPriceRepository dailyPriceRepository;
    @Autowired private StockRepository stockRepository;

    @Test
    @DisplayName("DB 데이터가 BarSeries로 정확히 변환되는지 확인")
    void convert(){
        // given
        String stockCode = "005930";
        saveDummyData(stockCode);

        // when
        BarSeries series = backtestService.runStrategy(stockCode);

        // then
        Assertions.assertThat(series).isNotNull();
    }

    private void saveDummyData(String stockCode) {
        // 1. Stock 마스터 데이터 저장 (DailyPrice가 참조해야 하므로)
        Stock stock = Stock.builder()
                .stockCode(stockCode)
                .stockName("삼성전자")
                .build();
        stockRepository.save(stock); // Repository 주입 필요

        // 2. 1년치까지는 아니더라도 변환 로직 확인을 위한 데이터 2~3개 저장
        DailyPrice day1 = DailyPrice.builder()
                .stock(stock)
                .date(LocalDate.of(2026, 3, 1))
                .openPrice(50000).highPrice(51000).lowPrice(49000).closePrice(50500)
                .volume(1000000)
                .build();

        DailyPrice day2 = DailyPrice.builder()
                .stock(stock)
                .date(LocalDate.of(2026, 3, 2))
                .openPrice(50500).highPrice(52000).lowPrice(50000).closePrice(51500)
                .volume(1200000)
                .build();

        dailyPriceRepository.saveAll(List.of(day1, day2));
    }
}