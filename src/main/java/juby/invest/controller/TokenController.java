package juby.invest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.dto.TokenDto;
import juby.invest.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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

    @Operation(summary = "KIS AccessToken 발급", description = "한국투자증권 서버에 요청하여 Access Token을 발급받아 반환합니다.")
    @GetMapping
    public ResponseEntity<TokenDto.TokenResponse> getToken(){
        TokenDto.TokenResponse response = tokenService.getMockAccessToken();
        return ResponseEntity.ok(response);
    }
}
