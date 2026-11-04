package juby.invest.domain.marketindex.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.domain.marketindex.dto.MarketIndexDto;
import juby.invest.domain.marketindex.exception.code.MarketIndexSuccessCode;
import juby.invest.domain.marketindex.service.MarketIndexService;
import juby.invest.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "시장 지수 API", description = "코스피·코스닥 지수를 조회한다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/market-indices")
public class MarketIndexController {

    private final MarketIndexService marketIndexService;

    @Operation(summary = "시장 지수 조회",
            description = "코스피·코스닥 지수와 전일대비율을 반환한다. 장중에는 실시간 체결가를, 그 밖의 시간에는 KIS 현재지수를 쓴다. "
                    + "항목의 realtime 값으로 어느 쪽인지 구분할 수 있다.")
    @GetMapping
    public ApiResponse<MarketIndexDto.MarketIndexRes> getMarketIndices() {
        return ApiResponse.onSuccess(MarketIndexSuccessCode.MARKET_INDEX_OK, marketIndexService.getMarketIndices());
    }
}
