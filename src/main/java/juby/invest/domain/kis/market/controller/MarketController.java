package juby.invest.domain.kis.market.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.domain.kis.market.dto.*;
import juby.invest.domain.kis.market.exception.code.MarketSuccessCode;
import juby.invest.domain.kis.market.service.*;
import juby.invest.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "주식 조회 API", description = "주식의 각종 정보를 조회한다.")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/market")
public class MarketController {

    private final MarketService marketService;

    @Operation(summary = "주식 현재가 및 전일대비 증감 조회", description = "종목 코드를 입력 받아 현재가와 전일대비 증감액을 반환한다.")
    @GetMapping("/{stockCode}/price")
    public ApiResponse<CurrentPriceRes.Info> getPrice(
            @Parameter(description = "종목 코드 (6자리 숫자)", example = "005930")
            @PathVariable String stockCode) throws InterruptedException {
        return ApiResponse.onSuccess(MarketSuccessCode.CURRENT_PRICE_OK, marketService.getDailyPrice(stockCode));
    }

    @Operation(summary = "주식 거래량 조회", description = "주식의 거래량 TOP 30 목록을 반환한다.")
    @GetMapping("/volume-rank")
    public ApiResponse<List<TradingVolumeDto.Output>> getTradingVolume() throws InterruptedException {
        return ApiResponse.onSuccess(MarketSuccessCode.TRADE_VOLUME_OK, marketService.getTradingVolume());
    }

    @Operation(summary = "주식현재가 일자별 조회", description = "종목의 30일 OHLCV정보를 조회한다.")
    @GetMapping("/daily-stock")
    public ApiResponse<List<DailyPriceRes.DailyInfo>> getDailyStockPrice(@Parameter(description = "종목 코드")
            @RequestParam String stockCode) throws InterruptedException {
        return ApiResponse.onSuccess(MarketSuccessCode.DAILY_PRICE_OK, marketService.getDailyStockPrice(stockCode));
    }

    @Operation(summary = "국내주식기간별시세조회API", description = "해당 종목의 기간별(최대 100개) 시세를 조회한다.")
    @GetMapping("/daily_itemchartprice")
    public ApiResponse<List<PeriodDailyPriceDto.Output>> getPeriodStockPrice(
            @Parameter(description = "종목코드(005930)") @RequestParam("stockcode") String stockCode,
            @Parameter(description = "시작날짜(20250303)") @RequestParam("startdate") String startDate,
            @Parameter(description = "종료날짜(20250310") @RequestParam("enddate") String endDate) throws InterruptedException {
        return ApiResponse.onSuccess(MarketSuccessCode.PERIOD_DAILY_PRICE_OK, marketService.getPeriodStockPrice(stockCode, startDate, endDate));
    }

    @Operation(summary = "국내휴장일조회API", description = "영업일,거래일 여부를 조회한다.")
    @GetMapping("/holiday")
    public ApiResponse<List<HolidayDto.Output>> checkHolidayList(
            @Parameter(description = "기준일자") @RequestParam("basedate") String baseDate
    ) throws InterruptedException {
        return ApiResponse.onSuccess(MarketSuccessCode.HOLIDAY_OK, marketService.getHolidayList(baseDate));
    }
}
