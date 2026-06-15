package juby.invest.domain.kis.token.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.domain.kis.token.dto.TokenDto;
import juby.invest.domain.kis.token.exception.code.TokenSuccessCode;
import juby.invest.domain.kis.token.service.TokenService;
import juby.invest.domain.member.enums.Role;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import juby.invest.global.apiPayload.code.GeneralErrorCode;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증 API", description = "KIS API 통신을 위한 토큰 발급 및 관리")
@RestController
@Slf4j
@RequestMapping("/api/token")
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;

    @Operation(summary = "KIS mockAccessToken 발급", description = "KIS 접근토큰발급 API (모의 도메인 - mockAccessToken)")
    @GetMapping("/mock")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<TokenDto.TokenResponse> getMockAccessToken() throws InterruptedException {
        BaseSuccessCode successCode = TokenSuccessCode.MOCK_TOKEN_SUCCESS;
        return ApiResponse.onSuccess(successCode, tokenService.getMockAccessToken());
    }

    @Operation(summary = "KIS realAccessToken 발급", description = "KIS 접근토큰발급 API (실전 도메인 - realAccessToken)")
    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/real")
    public ApiResponse<TokenDto.TokenResponse> getRealAccessToken() throws InterruptedException {
        BaseSuccessCode successCode = TokenSuccessCode.REAL_TOKEN_SUCCESS;
        return ApiResponse.onSuccess(successCode, tokenService.getRealAccessToken());
    }
}
