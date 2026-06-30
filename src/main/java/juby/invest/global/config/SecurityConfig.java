package juby.invest.global.config;

import juby.invest.global.security.exception.CustomAccessDenied;
import juby.invest.global.security.exception.CustomEntryPoint;
import juby.invest.global.security.handler.OAuth2SuccessHandler;
import juby.invest.global.security.service.CustomOAuth2MemberService;
import juby.invest.global.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomOAuth2MemberService customOAuth2MemberService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomEntryPoint customEntryPoint;
    private final CustomAccessDenied customAccessDenied;

    // 허용 url
    private final String[] allowUris = {
            "/",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/error/**",
            "/api/market/**",
            "/mypage.html" // mypage 정보 열람 테스트용
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .formLogin(AbstractHttpConfigurer::disable)

                .httpBasic(AbstractHttpConfigurer::disable)

                .oauth2Login((oauth2) -> oauth2
                        .loginPage("/")
                        .successHandler(oAuth2SuccessHandler)
                        .failureUrl("/member/failureLogin")
                        .userInfoEndpoint(userInfoEndpoint -> userInfoEndpoint
                                .userService(customOAuth2MemberService)))

                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers(allowUris).permitAll()
                        .anyRequest().authenticated())

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 로그아웃 시 홈으로 리다이렉트
                .logout(httpSecurityLogoutConfigurer ->
                        httpSecurityLogoutConfigurer.logoutSuccessUrl("/"))

                // 401 UNAUTHORIZED, 403 FORBIDDEN 예외 처리 필터
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customEntryPoint)
                        .accessDeniedHandler(customAccessDenied));

        return http.build();
    }
}
