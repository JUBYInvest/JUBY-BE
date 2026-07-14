package juby.invest.global.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import juby.invest.global.security.dto.LoginResDto;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import juby.invest.global.apiPayload.code.GeneralSuccessCode;
import juby.invest.global.security.entity.CustomOAuth2User;
import juby.invest.global.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // SecurityContext에서 OAuth 인증 객체 가져오기
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        Long userId = oAuth2User.getId();
        String role = oAuth2User.getRole().name();
        String name = oAuth2User.getName();

        // userId, role, name을 활용하여 JWT 생성
        String accessToken = jwtUtil.createAccessToken(userId, role, name);
        String refreshToken = jwtUtil.createRefreshToken(userId);

        log.info("accessToken: {}", accessToken);
        log.info("refreshToken: {}", refreshToken);

        // 생성된 accessToken과 refreshToken 응답.
        ObjectMapper objectMapper = new ObjectMapper();
        BaseSuccessCode successCode = GeneralSuccessCode.OK;

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(successCode.getStatus().value());

        ApiResponse<LoginResDto.LoginRes> responseBody = ApiResponse.onSuccess(
                successCode, LoginResDto.LoginRes.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .build());

        // 응답 출력
        objectMapper.writeValue(response.getOutputStream(), responseBody);
    }
}
