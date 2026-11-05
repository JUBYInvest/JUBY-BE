package juby.invest.domain.community.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.domain.community.dto.CommunityStockResDto;
import juby.invest.domain.community.exception.code.CommunitySuccessCode;
import juby.invest.domain.community.service.CommunityBoardService;
import juby.invest.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/community/stocks")
@Tag(name = "커뮤니티 종목 API", description = "커뮤니티 상단 종목 카드 줄을 제공한다. 비로그인도 가능하다.")
public class CommunityStockController {

    private final CommunityBoardService communityBoardService;

    @Operation(summary = "커뮤니티 종목 카드 조회",
            description = "전체 글 수와, 글이 많은 종목 순으로 최대 10개 종목의 글 수와 등락률을 반환한다. " +
                    "등락률은 최대 5분 전 시세이며 priceAsOf가 그 기준 시각이다.")
    @GetMapping
    public ApiResponse<CommunityStockResDto.StockBoard> getStockBoard() {
        return ApiResponse.onSuccess(CommunitySuccessCode.STOCK_BOARD_OK, communityBoardService.getStockBoard());
    }
}
