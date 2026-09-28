package juby.invest.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.domain.auth.dto.ReissueDto;
import juby.invest.domain.auth.exception.code.AuthSuccessCode;
import juby.invest.domain.auth.service.AuthService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.security.entity.CustomOAuth2User;
import juby.invest.global.security.util.CookieUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Slf4j
@Tag(name = "인증 API", description = "AT 재발급 / 로그아웃 기능을 구현한다.")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    /***
     * 함수 기능: 쿠키를 통해 전달받은 RT로 AT와 RT를 재발급한다.
     * @param refreshToken 쿠키에 담긴 RT
     * @return AtInfo accessToken
     */
    @Operation(summary = "토큰 재발급 API", description = "쿠키에 담긴 RT로 AT, RT를 재발급한다.")
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<ReissueDto.AtInfo>> reissue(
        @Parameter(hidden = true)
        @CookieValue(value = "refreshToken", required = false) String refreshToken
    ){
        ReissueDto.ReissueRes dto = authService.reissue(refreshToken);

        // 새 RT는 쿠키로, AT는 바디로 내려준다.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createRTCookie(dto.refreshToken()))
                .body(ApiResponse.onSuccess(AuthSuccessCode.REISSUE_OK, new ReissueDto.AtInfo(dto.accessToken())));
    }

    /***
     * 함수 기능: 로그아웃을 진행한다. 기존 AT는 블랙리스트에 추가하고 RT는 삭제한다.
     * @param user 회원
     * @return null
     */
    @Operation(summary = "로그아웃 API", description = "로그아웃을 진행한다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal CustomOAuth2User user
            ){

        authService.logout(user);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.deleteRTCookie())
                .body(ApiResponse.onSuccess(AuthSuccessCode.LOGOUT_OK, null));
    }
}
