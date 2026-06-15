package juby.invest.domain.kis.market.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.domain.kis.market.dto.*;
import juby.invest.domain.kis.market.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "주식 조회 API", description = "주식의 각종 정보를 조회한다.")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/market")
public class MarketController {

    private final MarketService marketService;

    @Operation(summary = "주식 현재가 및 전일대비 증감 조회", description = "종목 코드를 입력 받아 현재가와 전일대비 증감액을 반환한다.")
    @GetMapping("/price")
    public ResponseEntity<DailyPriceDto.Output> getPrice(@Parameter(description = "종목 코드")
            @RequestParam String code) throws InterruptedException {
        DailyPriceDto.Output response = marketService.getDailyPrice(code);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "주식 거래량 조회", description = "주식의 거래량 TOP 30 목록을 반환한다.")
    @GetMapping("/volume-rank")
    public ResponseEntity<List<TradingVolumeDto.Output>> getTradingVolume() throws InterruptedException {
        List<TradingVolumeDto.Output> response = marketService.getTradingVolume();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "주식현재가 일자별 조회", description = "종목의 30일 OHLCV정보를 조회한다.")
    @GetMapping("/daily-stock")
    public ResponseEntity<List<DailyStockPriceDto.Output>> getDailyStockPrice(@Parameter(description = "종목 코드")
            @RequestParam String stockCode) throws InterruptedException {
        List<DailyStockPriceDto.Output> dailyStockPrice = marketService.getDailyStockPrice(stockCode);
        return ResponseEntity.ok(dailyStockPrice);
    }

    @Operation(summary = "국내주식기간별시세조회API", description = "해당 종목의 기간별(최대 100개) 시세를 조회한다.")
    @GetMapping("/daily_itemchartprice")
    public ResponseEntity<List<PeriodDailyPriceDto.Output>> getPeriodStockPrice(
            @Parameter(description = "종목코드(005930)") @RequestParam("stockcode") String stockCode,
            @Parameter(description = "시작날짜(20250303)") @RequestParam("startdate") String startDate,
            @Parameter(description = "종료날짜(20250310") @RequestParam("enddate") String endDate) throws InterruptedException {
        List<PeriodDailyPriceDto.Output> periodStockPrice = marketService.getPeriodStockPrice(stockCode, startDate, endDate);
        return ResponseEntity.ok(periodStockPrice);
    }

    @Operation(summary = "국내휴장일조회API", description = "영업일,거래일 여부를 조회한다.")
    @GetMapping("/holiday")
    public ResponseEntity<List<HolidayDto.Output>> checkHolidayList(
            @Parameter(description = "기준일자") @RequestParam("basedate") String baseDate
    ) throws InterruptedException {
        List<HolidayDto.Output> holidayList = marketService.getHolidayList(baseDate);
        return ResponseEntity.ok(holidayList);
    }
}
