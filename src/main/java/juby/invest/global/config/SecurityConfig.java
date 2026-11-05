package juby.invest.global.config;

import juby.invest.domain.auth.service.AuthService;
import juby.invest.global.security.exception.CustomAccessDenied;
import juby.invest.global.security.exception.CustomEntryPoint;
import juby.invest.global.security.handler.OAuth2FailureHandler;
import juby.invest.global.security.handler.OAuth2SuccessHandler;
import juby.invest.global.security.service.CustomOAuth2MemberService;
import juby.invest.global.security.filter.JwtAuthenticationFilter;
import juby.invest.global.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomOAuth2MemberService customOAuth2MemberService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final CustomEntryPoint customEntryPoint;
    private final CustomAccessDenied customAccessDenied;
    private final JwtUtil jwtUtil;
    private final AuthService authService;

    /***
     * 인증 없이 열리는 url.
     * <p>
     * 공개 시장 데이터(주가/지수/실시간 스트림)는 로그인과 무관하게 보여야 하므로 여기에 둔다.
     * 이 목록을 좁힐 때는 SecurityConfigAllowUriTest가 지키는 경로를 함께 확인해야 한다.
     */
    static final String[] allowUris = {
            "/",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/error/**",
            "/mypage.html", // mypage 정보 열람 테스트용
            "/openai-test.html", // openai 챗봇 테스트용
            "/backtest-test.html", // 백테스트 프리셋 결과 조회 테스트용
            "/community-test.html", // 커뮤니티 게시물 테스트용
            "/api/stocks/**", // 메인 페이지 (주가), 상세 페이지 (종목 데이터, 뉴스 정보)
            "/api/market-indices", // 홈 화면 코스피/코스닥 지수
            // 실시간 시세 SSE 스트림. 브라우저 EventSource는 커스텀 헤더를 보낼 수 없어
            // Authorization 헤더로 인증할 방법이 아예 없다. 공개 시세라 인증 없이 연다.
            "/api/stream/**",
            "/api/auth/reissue" // AT 재발급
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .cors(Customizer.withDefaults())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .formLogin(AbstractHttpConfigurer::disable)

                .httpBasic(AbstractHttpConfigurer::disable)

                .oauth2Login((oauth2) -> oauth2
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                        .userInfoEndpoint(userInfoEndpoint -> userInfoEndpoint
                                .userService(customOAuth2MemberService)))

                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers(allowUris).permitAll()
                        // 댓글은 보기도 로그인한 회원만 가능하다. 먼저 맞는 규칙이 이기므로 아래 permitAll보다 앞에 둔다.
                        .requestMatchers(HttpMethod.GET, "/api/community/posts/*/comments").authenticated()
                        // 커뮤니티는 조회만 비로그인 허용. 작성/수정/삭제/좋아요는 아래 authenticated()에 걸린다.
                        .requestMatchers(HttpMethod.GET, "/api/community/**").permitAll()
                        .anyRequest().authenticated())

                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, authService), UsernamePasswordAuthenticationFilter.class)

                .logout(AbstractHttpConfigurer::disable)

                // 401 UNAUTHORIZED, 403 FORBIDDEN 예외 처리 필터
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customEntryPoint)
                        .accessDeniedHandler(customAccessDenied));
        return http.build();
    }
}
