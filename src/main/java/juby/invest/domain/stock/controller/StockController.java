package juby.invest.domain.stock.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import juby.invest.domain.backtest.dto.LeadingStockResDto;
import juby.invest.domain.stock.dto.StockDetailDto;
import juby.invest.domain.stock.dto.StockListDto;
import juby.invest.domain.stock.dto.StockNewsDto;
import juby.invest.domain.stock.dto.StockSearchDto;
import juby.invest.domain.stock.enums.Period;
import juby.invest.domain.stock.exception.code.StockSuccessCode;
import juby.invest.domain.stock.service.StockService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "홈 종목 종가 배너 / 종목 상세 페이지", description = "금일 종가 조회 / 종목 상세정보(OHLCV) 조회/ 뉴스 데이터 조회")
public class StockController {

    private final StockService stockService;

    /***
     * 함수 기능: 모든 종목에 대한 종목코드, 종목명, 종가, 등락률, 거래대금 정보를 조회한다.
     *           16시 이전에는 전일 정보, 16시 이후에는 금일 정보를 보여준다.
     */
    @GetMapping
    @Operation(summary = "메인 종목 종가 조회 API", description = "메인 페이지에서 100개 종목에 대한 정보(번호, 종목명, 종가, 등락률, 거래대금)들을 제공한다.")
    public ApiResponse<StockListDto.StockListRes> getStockList(
            @AuthenticationPrincipal CustomOAuth2User user,
            @Valid @ParameterObject @ModelAttribute StockListDto.StockListReq stockListReq
            ){
        return ApiResponse.onSuccess(StockSuccessCode.STOCK_LIST_OK, stockService.getStockList(user, stockListReq));
    }

    @GetMapping("/leading-stocks")
    @Operation(summary = "홈 화면 테마별 대장주 수익률 조회 API", description = "기술주, 방산주, 바이오주의 SMA 전략 1년 백테스트 누적 수익률을 제공한다.")
    public ApiResponse<LeadingStockResDto.LeadingStockRes> getLeadingStocks(){
        return ApiResponse.onSuccess(StockSuccessCode.LEADING_STOCK_OK, stockService.getLeadingStocks());
    }

    /***
     * 함수 기능: 종목명에 검색어가 포함된 종목을 조회한다. (챗봇 종목 선택 자동완성용)
     * @param keyword 검색어 (1~50자)
     * @return 종목코드, 종목명 목록 (최대 10개)
     */
    @GetMapping("/search")
    @Operation(summary = "종목명 검색 API",
            description = "종목명에 검색어가 포함된 종목을 최대 10개 반환한다. 검색어로 시작하는 종목이 먼저 온다. " +
                    "챗봇 종목 선택 자동완성에 사용하며, 선택한 stockName을 /api/open-ai/ask 요청에 담아 보내면 된다.")
    public ApiResponse<List<StockSearchDto.StockSearchItem>> searchStocks(
            @RequestParam
            @NotBlank(message = "검색어는 필수입니다.")
            @Size(max = 50, message = "검색어는 50자 이하로 입력해주세요.")
            String keyword
    ){
        return ApiResponse.onSuccess(StockSuccessCode.STOCK_SEARCH_OK, stockService.searchStocks(keyword));
    }

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
