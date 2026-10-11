package juby.invest.global.logging;

import ch.qos.logback.classic.Level;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MdcLoggingFilter (요청 추적 MDC / 완료 로그)")
class MdcLoggingFilterTest {

    private static final String TRACE_ID_HEADER = "X-Request-Id";

    private final MdcLoggingFilter filter = new MdcLoggingFilter();

    private LogCapture logs;

    @BeforeEach
    void setUp() {
        logs = LogCapture.attachTo(MdcLoggingFilter.class);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        logs.detach();
        MDC.clear();
    }

    private static MockHttpServletRequest get(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    /***
     * 체인 실행 시점의 MDC를 떠두는 FilterChain.
     * 필터가 finally에서 MDC를 비우므로, 체인 안에서 보지 않으면 확인할 수 없다.
     */
    private static final class CapturingChain implements FilterChain {

        private final int status;
        private Map<String, String> mdc;
        private boolean called;

        private CapturingChain(int status) {
            this.status = status;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            called = true;
            Map<String, String> snapshot = MDC.getCopyOfContextMap();
            mdc = snapshot == null ? Map.of() : Map.copyOf(snapshot);
            ((HttpServletResponse) response).setStatus(status);
        }
    }

    @Nested
    @DisplayName("traceId")
    class TraceId {

        @Test
        @DisplayName("X-Request-Id 헤더가 오면 그 값을 그대로 쓴다")
        void reusesInboundHeader() throws Exception {
            MockHttpServletRequest request = get("/api/stocks");
            request.addHeader(TRACE_ID_HEADER, "nginx-request-id");
            MockHttpServletResponse response = new MockHttpServletResponse();
            CapturingChain chain = new CapturingChain(200);

            filter.doFilter(request, response, chain);

            // nginx의 $request_id를 받아 써야 nginx access log와 앱 로그를 같은 ID로 묶을 수 있다.
            assertThat(chain.mdc).containsEntry("traceId", "nginx-request-id");
        }

        @Test
        @DisplayName("헤더가 없으면 새로 생성한다")
        void generatesWhenHeaderAbsent() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            CapturingChain chain = new CapturingChain(200);

            filter.doFilter(get("/api/stocks"), response, chain);

            assertThat(chain.mdc.get("traceId")).isNotBlank();
        }

        @Test
        @DisplayName("응답 헤더로 되돌려준다")
        void echoesBackInResponseHeader() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            CapturingChain chain = new CapturingChain(200);

            filter.doFilter(get("/api/stocks"), response, chain);

            // 사용자가 장애를 제보할 때 이 값으로 로그를 특정한다.
            assertThat(response.getHeader(TRACE_ID_HEADER)).isEqualTo(chain.mdc.get("traceId"));
        }
    }

    @Nested
    @DisplayName("clientIp")
    class ClientIp {

        @Test
        @DisplayName("X-Real-IP 값을 쓴다")
        void usesXRealIp() throws Exception {
            MockHttpServletRequest request = get("/api/stocks");
            request.addHeader("X-Real-IP", "203.0.113.7");
            CapturingChain chain = new CapturingChain(200);

            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(chain.mdc).containsEntry("clientIp", "203.0.113.7");
        }

        @Test
        @DisplayName("X-Real-IP가 없으면 UNKNOWN으로 남긴다")
        void fallsBackToUnknown() throws Exception {
            CapturingChain chain = new CapturingChain(200);

            filter.doFilter(get("/api/stocks"), new MockHttpServletResponse(), chain);

            // getRemoteAddr()로 채우면 프록시 IP가 클라이언트 IP처럼 보인다.
            // UNKNOWN이면 "nginx를 거치지 않은 요청"이라는 사실이 그대로 드러난다.
            assertThat(chain.mdc).containsEntry("clientIp", "UNKNOWN");
        }
    }

    @Nested
    @DisplayName("MDC 생애주기")
    class Lifecycle {

        @Test
        @DisplayName("요청 정보를 MDC에 심는다")
        void putsRequestInfo() throws Exception {
            CapturingChain chain = new CapturingChain(200);

            filter.doFilter(new MockHttpServletRequest("POST", "/api/members/me"), new MockHttpServletResponse(), chain);

            assertThat(chain.mdc)
                    .containsEntry("httpMethod", "POST")
                    .containsEntry("uri", "/api/members/me");
        }

        @Test
        @DisplayName("필터를 통과하면 MDC가 비어 있다")
        void clearsAfterRequest() throws Exception {
            filter.doFilter(get("/api/stocks"), new MockHttpServletResponse(), new CapturingChain(200));

            // 지우지 않으면 재사용된 스레드가 다음 요청에서 이전 요청의 traceId/userId를 상속한다.
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        }

        @Test
        @DisplayName("이전 요청이 남긴 MDC를 물려받아도 진입 시 비운다")
        void clearsInheritedContextOnEntry() throws Exception {
            // shouldNotFilter로 제외된 경로에서는 이 필터가 돌지 않아 MDC를 정리할 주체가 없다.
            // 그 경로에서 JwtAuthenticationFilter가 심은 userId가 스레드에 남을 수 있다.
            MDC.put("userId", "999");
            CapturingChain chain = new CapturingChain(200);

            filter.doFilter(get("/api/stocks"), new MockHttpServletResponse(), chain);

            assertThat(chain.mdc).doesNotContainKey("userId");
        }

        @Test
        @DisplayName("체인에서 예외가 나도 MDC를 비우고 예외를 그대로 올린다")
        void clearsAndRethrowsOnException() {
            FilterChain boom = (request, response) -> {
                throw new IllegalStateException("boom");
            };

            assertThatThrownBy(() -> filter.doFilter(get("/api/stocks"), new MockHttpServletResponse(), boom))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        }
    }

    @Nested
    @DisplayName("완료 로그")
    class CompletionLog {

        @Test
        @DisplayName("요청 1건당 1줄만 남긴다")
        void logsExactlyOneLine() throws Exception {
            filter.doFilter(get("/api/stocks"), new MockHttpServletResponse(), new CapturingChain(200));

            assertThat(logs.events()).hasSize(1);
        }

        @Test
        @DisplayName("MDC가 살아 있는 시점에 남긴다")
        void logsWhileMdcAlive() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(get("/api/stocks"), response, new CapturingChain(404));

            // 완료 로그가 MDC.clear() 뒤에 남으면 status/latencyMs 필드가 전부 유실된다.
            assertThat(logs.lastMdc())
                    .containsEntry("status", "404")
                    .containsKeys("traceId", "latencyMs");
        }

        @Test
        @DisplayName("2xx는 INFO로 남긴다")
        void infoFor2xx() throws Exception {
            filter.doFilter(get("/api/stocks"), new MockHttpServletResponse(), new CapturingChain(200));

            assertThat(logs.lastEvent().getLevel()).isEqualTo(Level.INFO);
        }

        @Test
        @DisplayName("4xx는 클라이언트 책임이므로 INFO로 남긴다")
        void infoFor4xx() throws Exception {
            filter.doFilter(get("/api/stocks/nope"), new MockHttpServletResponse(), new CapturingChain(404));

            assertThat(logs.lastEvent().getLevel()).isEqualTo(Level.INFO);
        }

        @Test
        @DisplayName("5xx는 WARN까지만 올린다 (ERROR는 advice가 남기므로 중복 알림이 된다)")
        void warnFor5xx() throws Exception {
            filter.doFilter(get("/api/stocks"), new MockHttpServletResponse(), new CapturingChain(500));

            assertThat(logs.lastEvent().getLevel()).isEqualTo(Level.WARN);
        }

        @Test
        @DisplayName("체인 밖으로 빠져나온 예외는 ERROR로 남긴다")
        void errorForEscapedException() {
            FilterChain boom = (request, response) -> {
                throw new IllegalStateException("보안 필터에서 터진 예외");
            };

            assertThatThrownBy(() -> filter.doFilter(get("/api/stocks"), new MockHttpServletResponse(), boom))
                    .isInstanceOf(IllegalStateException.class);

            // 이 예외는 @RestControllerAdvice에 닿지 않으므로 이 로그가 스택트레이스의 유일한 기록이다.
            assertThat(logs.lastEvent().getLevel()).isEqualTo(Level.ERROR);
            assertThat(logs.lastEvent().getThrowableProxy()).isNotNull();
        }
    }

    @Nested
    @DisplayName("제외 경로 (shouldNotFilter)")
    class ExcludedUris {

        @Test
        @DisplayName("헬스체크·문서·정적 리소스는 로그를 남기지 않는다")
        void skipsNoisyUris() throws Exception {
            List<String> excluded = List.of(
                    "/actuator/health",
                    "/swagger-ui/index.html",
                    "/v3/api-docs",
                    "/v3/api-docs/swagger-config",
                    "/webjars/swagger-ui/index.css",
                    "/favicon.ico",
                    "/mypage.html"
            );

            for (String uri : excluded) {
                filter.doFilter(get(uri), new MockHttpServletResponse(), new MockFilterChain());
            }

            assertThat(logs.events()).isEmpty();
        }

        @Test
        @DisplayName("제외 경로라도 체인은 그대로 이어준다")
        void stillPassesDownTheChain() throws Exception {
            CapturingChain chain = new CapturingChain(200);

            filter.doFilter(get("/favicon.ico"), new MockHttpServletResponse(), chain);

            assertThat(chain.called).isTrue();
            assertThat(chain.mdc).isEmpty();
        }

        @Test
        @DisplayName("루트(/)와 API 경로는 제외하지 않는다")
        void doesNotSkipPages() throws Exception {
            filter.doFilter(get("/"), new MockHttpServletResponse(), new CapturingChain(200));
            filter.doFilter(get("/api/stocks"), new MockHttpServletResponse(), new CapturingChain(200));

            assertThat(logs.events()).hasSize(2);
        }
    }
}