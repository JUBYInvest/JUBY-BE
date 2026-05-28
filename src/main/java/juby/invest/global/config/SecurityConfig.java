package juby.invest.global.config;

import juby.invest.global.security.handler.OAuth2SuccessHandler;
import juby.invest.global.security.service.CustomOAuth2MemberService;
import juby.invest.global.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2MemberService customOAuth2MemberService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // 허용 url
    private final String[] allowUris = {
            "/",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/error/**",
            "/v1/**",
            "/api/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable);

        // 세션 생성 x
        http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http
                .formLogin(AbstractHttpConfigurer::disable);

        http
                .httpBasic(AbstractHttpConfigurer::disable);

        http
                .oauth2Login((oauth2) -> oauth2
                        .loginPage("/")
                        .successHandler(oAuth2SuccessHandler)
                        .failureUrl("/member/failureLogin")
                        .userInfoEndpoint(userInfoEndpoint -> userInfoEndpoint
                                .userService(customOAuth2MemberService)));

        http
                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers(allowUris).permitAll() // 나중에는 v1, api 삭제해야함.
                        .anyRequest().authenticated());

        http
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // 로그아웃 시 홈으로 리다이렉트
        http
                .logout(httpSecurityLogoutConfigurer ->
                        httpSecurityLogoutConfigurer.logoutSuccessUrl("/"));

        return http.build();
    }
}
