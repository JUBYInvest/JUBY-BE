package juby.invest.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import juby.invest.domain.member.dto.LikeStockDto;
import juby.invest.domain.member.dto.LikeStockListDto;
import juby.invest.domain.member.exception.code.likeStock.LikeStockSuccessCode;
import juby.invest.domain.member.service.LikeStockService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
@Tag(name = "관심 종목 API", description = "관심 종목 등록/삭제/목록 조회 기능을 수행한다.")
public class LikeStockController {

    private final LikeStockService likeStockService;

    @Operation(summary = "관심 종목 등록", description = "관심 종목을 등록한다.")
    @PostMapping("/me/like-stocks")
    public ApiResponse<LikeStockDto.LikeStockRes> addLikeStock(
            @AuthenticationPrincipal CustomOAuth2User user,
            @Valid @RequestBody LikeStockDto.LikeStockReq req){
        return ApiResponse.onSuccess(LikeStockSuccessCode.ADD_LIKE_SUCCESS, likeStockService.addLikeStock(user, req));
    }

    @Operation(summary = "관심 종목 삭제", description = "관심 종목을 삭제한다.")
    @DeleteMapping("/me/like-stocks/{stockCode}")
    public ApiResponse<LikeStockDto.LikeStockRes> deleteLikeStock(
            @AuthenticationPrincipal CustomOAuth2User user,
            @PathVariable String stockCode
    ){
        return ApiResponse.onSuccess(LikeStockSuccessCode.DELETE_LIKE_SUCCESS, likeStockService.deleteLikeStock(user, stockCode));
    }

    @Operation(summary = "관심 종목 목록 조회", description = "관심 종목 목록을 조회한다.")
    @GetMapping("/me/like-stocks")
    public ApiResponse<LikeStockListDto.LikeStockListRes> getLikeStockList(
            @AuthenticationPrincipal CustomOAuth2User user
    ){
        return ApiResponse.onSuccess(LikeStockSuccessCode.GET_LIKELIST_SUCCESS, likeStockService.getLikeStockList(user));
    }
}
