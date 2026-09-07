package juby.invest.global.security.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import juby.invest.domain.auth.exception.code.AuthErrorCode;
import juby.invest.global.security.entity.CustomOAuth2User;
import juby.invest.global.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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

    private final JwtUtil jwtUtil;

    /***
     * 1. Request Header 부분에서 token만 추출한다.
     * 2. 추출한 토큰이 올바른 지 JwtUtil 통해 검증한다.
     * 3. JwtUtil를 통해 Authentication 객체를 생성한다.
     * 4. SecurityContext에 Authentication 객체를 담는다.
     * 5. doFilter를 통해 다음 필터로 넘긴다.
     * @param request 요청
     * @param response 응답
     * @param filterChain 필터 체인
     * @throws ServletException 서블릿 예외
     * @throws IOException I/O 예외
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        // i) 요청 헤더에 AT가 없을 경우 -> 다음 필터로 넘기고, 이후 인증 판단.
        // ii) 요청 헤더에 AT가 있을 경우 -> AT가 유효할 경우 -> 인증 객체를 저장하고 다음 필터로
        //                                AT가 무효할 경우 -> ExceptionTranslationFilter에서 잡힌다. (인증이 필요한 경로에 한해서만)
        if (token != null){

            try {
                Claims claims = jwtUtil.parseClaims(token);

                Authentication authentication = jwtUtil.getAuthentication(claims);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
                log.info("userId={}", principal.getId());
            } catch (ExpiredJwtException e){
                log.warn("만료된 토큰입니다. {}", e.getMessage());
                request.setAttribute("exception", AuthErrorCode.EXPIRED_TOKEN);
            } catch (JwtException | IllegalArgumentException e){
                log.warn("유효하지 않은 토큰입니다. {}", e.getMessage());
                request.setAttribute("exception", AuthErrorCode.INVALID_TOKEN);
            } catch (Exception e){
                log.warn("토큰 검증 중 오류가 발생했습니다. {}", e.getMessage());
                request.setAttribute("exception", AuthErrorCode.UNKNOWN_TOKEN_ERROR);
            }
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
