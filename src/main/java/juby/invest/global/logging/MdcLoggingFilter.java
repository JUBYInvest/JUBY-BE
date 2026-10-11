package juby.invest.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class MdcLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Request-Id";
    public static final String CLIENT_IP = "X-Real-IP";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    // 추적할 필요가 없는 경로들 (정적 리소스, 헬스체크 등)
    private static final String[] NO_LOG_URIS = {
            "/actuator/**",      // 헬스체크
            "/swagger-ui/**",    // API 문서 UI
            "/v3/api-docs/**",   // API 문서 스펙
            "/webjars/**",       // 문서 UI가 끌어가는 정적 리소스
            "/favicon.ico",
            "/*.html"            // static/ 하위 테스트 페이지 (mypage, openai-test, backtest-test, test)
    };

    /***
     * 함수 기능: 완료 로그를 남기지 않을 요청을 가려낸다.
     * @param request 요청
     * @return 제외 대상이면 true. 이 경우 필터 자체가 실행되지 않으므로 MDC도 심지 않는다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return Arrays.stream(NO_LOG_URIS)
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 제외 경로에서 JwtAuthenticationFilter가 심어둔 userId는 해당 필터의 finally가 실행되지 않기 때문에, 컨텍스트를 한번 더 비운다.
        MDC.clear();

        // 시작시간
        long start = System.nanoTime();

        // Nginx에서 TRACE_ID_HEADER를 자동으로 추가해주지만, 로컬에서는 Nginx가 동작하지 않으므로 추가한다.
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)){
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }

        MDC.put("traceId", traceId);
        MDC.put("httpMethod", request.getMethod());
        MDC.put("uri", request.getRequestURI());
        String clientIp = request.getHeader(CLIENT_IP);
        MDC.put("clientIp", StringUtils.hasText(clientIp) ? clientIp : "UNKNOWN");

        // 추후, 다른 서버를 통할 때, 요청을 식별할 traceId 삽입
        response.setHeader(TRACE_ID_HEADER, traceId);

        // 체인 밖으로 빠져나온 예외.
        // GeneralExceptionAdvice가 보지 못하고 ExceptionTranslation이 처리하기 못한 예외만 여기에 담긴다.
        Throwable failure = null;

        try{
            filterChain.doFilter(request, response);
        } catch (Throwable t){
            failure = t;
            throw t; // 로그만 남기고 흐름은 그대로 컨테이너에 넘긴다.
        } finally {

            // 소요시간
            long latency = (System.nanoTime() - start) / 1_000_000;
            int status = response.getStatus();

            MDC.put("status", String.valueOf(status));
            MDC.put("latencyMs", String.valueOf(latency));

            if (failure != null){ // SecurityFilterChain에서 터진 예외를 잡는다.
                log.error("{} {} -> 처리되지 않은 예외 ({}ms)", request.getMethod(), request.getRequestURI(), latency, failure);
            } else if (status >= 500){ //
                log.warn("{} {} -> {} ({}ms)", request.getMethod(), request.getRequestURI(), status, latency);
            } else {
                log.info("{} {} -> {} ({}ms)", request.getMethod(), request.getRequestURI(), status, latency);
            }

            MDC.clear();
        }
    }
}
