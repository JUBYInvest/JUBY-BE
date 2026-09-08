package juby.invest.global.config;

import juby.invest.global.security.exception.CustomAccessDenied;
import juby.invest.global.security.exception.CustomEntryPoint;
import juby.invest.global.security.handler.OAuth2FailureHandler;
import juby.invest.global.security.handler.OAuth2SuccessHandler;
import juby.invest.global.security.service.CustomOAuth2MemberService;
import juby.invest.global.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomOAuth2MemberService customOAuth2MemberService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomEntryPoint customEntryPoint;
    private final CustomAccessDenied customAccessDenied;

    // 허용 url
    private final String[] allowUris = {
            "/",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/error/**",
            "/mypage.html", // mypage 정보 열람 테스트용
            "/openai-test.html", // openai 챗봇 테스트용
            "/backtest-test.html", // 백테스트 프리셋 결과 조회 테스트용
            "/api/stocks/**", // 메인 페이지 (주가), 상세 페이지 (종목 데이터, 뉴스 정보)
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
                        .anyRequest().authenticated())

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .logout(AbstractHttpConfigurer::disable)

                // 401 UNAUTHORIZED, 403 FORBIDDEN 예외 처리 필터
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customEntryPoint)
                        .accessDeniedHandler(customAccessDenied));
        return http.build();
    }
}
