package juby.invest.global.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import juby.invest.global.security.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    /***
     *
     * @param request
     * @param response
     * @param filterChain
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 1. Request Header 부분에서 token만 추출한다.
        // 2. 추출한 토큰이 올바른 지 jwtTokenProvider를 통해 검증한다.
        // 3. jwtTokenProvider를 통해 Authentication 객체를 생성한다.
        // 4. SecurityContext에 Authentication 객체를 담는다.
        // 5. doFilter를 통해 다음 필터로 넘긴다.

        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)){

            Authentication authentication = jwtTokenProvider.getAuthentication(token);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.info("SecurityContext에 Authentication: {} 저장완료", authentication.getPrincipal());
        }

        filterChain.doFilter(request, response);
    }

    /***
     * HTTP Header에서 'Bearer ' 글자만 떼어내고 순수 토큰 값만 추출한다.
     * @param request
     * @return
     */
    private String resolveToken(HttpServletRequest request){

        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")){
            return bearerToken.substring(7);
        }
        return null;
    }
}
