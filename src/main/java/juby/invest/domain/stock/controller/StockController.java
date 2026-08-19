package juby.invest.domain.stock.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import juby.invest.domain.news.exception.code.NewsSuccessCode;
import juby.invest.domain.stock.dto.StockDetailDto;
import juby.invest.domain.stock.dto.StockNewsDto;
import juby.invest.domain.stock.enums.Period;
import juby.invest.domain.stock.exception.code.StockSuccessCode;
import juby.invest.domain.stock.service.StockService;
import juby.invest.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stocks")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "종목 상세 페이지", description = "종목 상세정보(OHLCV) 조회/ 뉴스 데이터 조회")
public class StockController {

    private final StockService stockService;

    /***
     * 함수 기능: 종목의 상세 정보를 조회한다. (기간의 OHLVC 데이터, 현재가, 전일 대비 변동률)
     * @param stockCode 종목 코드
     * @param period 기간 (1주, 1달, 3달, 6달, 1년, 3년, 모두)
     * @return StockDetailRes
     * @throws InterruptedException KIS API 호출 예외
     */
    @GetMapping("/{stockCode}")
    @Operation(summary = "종목 상세정보 조회 API", description = "종목 기간의 OHLCV 데이터를 제공한다.")
    public ApiResponse<StockDetailDto.StockDetailRes> getStockDetails(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "ALL") Period period
    ) throws InterruptedException {
        return ApiResponse.onSuccess(StockSuccessCode.STOCK_DETAIL_OK, stockService.getStockDetails(stockCode, period));
    }

    /***
     * 함수 기능: 종목의 뉴스 데이터를 조회한다. 해당 데이터들은 Pinecone에서 가져온다.
     * @param stockCode 종목 코드
     * @param stockNewsReq NewsSortType, page
     * @return StockNewsRes
     */
    @GetMapping("/{stockCode}/news")
    @Operation(summary = "종목별 관련 뉴스 조회 API", description = "종목코드로 관련 뉴스를 조회한다.")
    public ApiResponse<StockNewsDto.StockNewsRes> getStockNews(
            @PathVariable String stockCode,
            @Valid @ParameterObject @ModelAttribute StockNewsDto.StockNewsReq stockNewsReq
    ){
        return ApiResponse.onSuccess(StockSuccessCode.STOCK_NEWS_OK, stockService.getStockNews(stockCode, stockNewsReq));
    }
}
