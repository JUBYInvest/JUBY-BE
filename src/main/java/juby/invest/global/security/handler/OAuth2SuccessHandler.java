package juby.invest.global.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import juby.invest.global.security.enitty.CustomOAuth2User;
import juby.invest.global.security.util.JwtTokenProvider;
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

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        Long userId = oAuth2User.getUserId();
        String role = oAuth2User.getRole().name();
        String name = oAuth2User.getName();

        // userId, role, name을 활용하여 JWT 생성
        String accessToken = jwtTokenProvider.createAccessToken(userId, role, name);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        log.info("accessToken: {}", accessToken);
        log.info("refreshToken: {}", refreshToken);

        // AccessToken은 프론트엔드로 전달 & RefreshToken은 DB에 저장.
//        String redirectUrl = "http://localhost:8080";
//        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
