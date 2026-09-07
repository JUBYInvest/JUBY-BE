package juby.invest.global.security.handler;

import com.google.common.net.HttpHeaders;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import juby.invest.domain.auth.service.AuthService;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.global.security.entity.CustomOAuth2User;
import juby.invest.global.security.util.CookieUtil;
import juby.invest.global.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final MemberRepository memberRepository;
    private final AuthService authService;

    @Value("${app.oauth2.redirect-uri}") private String redirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // SecurityContext에서 인증 객체의 principal 가져오기
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        Member member = memberRepository.findById(oAuth2User.getId())
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        // userId, role, name을 활용하여 JWT 생성
        String accessToken = jwtUtil.createAccessToken(oAuth2User.getId(), oAuth2User.getRole().name(), oAuth2User.getName());
        String refreshToken = jwtUtil.createRefreshToken(oAuth2User.getId());

        log.info("토큰 발급 완료 - userId:{}", oAuth2User.getId());

        // 발급한 RT 저장 or 업데이트
        authService.saveOrUpdateRT(member, refreshToken);

        // 소셜 로그인 성공 시, 프론트 주소로 리다이렉트
        redirect(request, response, member, accessToken, refreshToken);
    }

    // 소설로그인 성공 후, 프론트로 회원 이름과 온보딩 여부, AT, RT 정보를 담아 리다이렉션 시킨다.
    private void redirect(
            HttpServletRequest request,
            HttpServletResponse response,
            Member member,
            String accessToken,
            String refreshToken) throws IOException {

        // 응답 헤더에 RT쿠키 추가
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.createRTCookie(refreshToken));

        // 프론트 URL로 리다이렉트 주소 조립
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUrl)
                .queryParam("name", member.getName())
                .queryParam("isOnboarded", member.isOnboarded())
                .build()
                .encode()
                .toUriString();
        targetUrl = targetUrl + "#accessToken=" + accessToken;

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
