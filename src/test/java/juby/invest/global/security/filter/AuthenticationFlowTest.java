package juby.invest.global.security.filter;

import jakarta.servlet.http.HttpServletRequest;
import juby.invest.domain.auth.service.AuthService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.GeneralSuccessCode;
import juby.invest.global.security.entity.CustomOAuth2User;
import juby.invest.global.security.exception.CustomEntryPoint;
import juby.invest.global.security.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 필터 체인을 실제 순서대로 조립해, AT 상태별로 어떤 응답이 나가는지 확인한다.
 * <p>
 * JwtAuthenticationFilter는 응답을 직접 쓰지 않고 실패 사유만 request attribute에 남기므로,
 * 필터 하나만 떼어 테스트하면 "그래서 사용자가 무슨 응답을 받는가"를 확인할 수 없다.
 * AuthorizationFilter가 막고 → ExceptionTranslationFilter가 잡고 → CustomEntryPoint가 렌더링하는
 * 경로 전체를 붙여야 응답 코드까지 검증된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("인증 필터 체인 (AT 상태별 응답)")
class AuthenticationFlowTest {

    private static final String SECRET = "juby-invest-test-secret-key-must-be-at-least-32-bytes-long";
    private static final String OTHER_SECRET = "attacker-forged-secret-key-also-at-least-32-bytes-long!!!";
    private static final long AT_VALIDITY = 1_800_000L;
    private static final long RT_VALIDITY = 1_209_600_000L;
    private static final long MEMBER_ID = 1L;

    @Mock
    private AuthService authService;

    private JwtUtil jwtUtil;
    private MockMvc secured;

    /** 인증이 필요한 경로를 흉내내는 컨트롤러. 인증 객체가 컨트롤러까지 살아오는지도 함께 본다. */
    @RestController
    public static class ProbeController {

        @GetMapping("/api/probe")
        public ApiResponse<Long> probe(@AuthenticationPrincipal CustomOAuth2User user) {
            return ApiResponse.onSuccess(GeneralSuccessCode.OK, user == null ? null : user.getId());
        }
    }

    /**
     * SecurityConfig가 만드는 체인과 같은 순서로 필터를 쌓는다.
     * ExceptionTranslationFilter가 AuthorizationFilter보다 "앞"에 있어야,
     * 뒤에서 올라온 AccessDeniedException을 try-catch로 받아 EntryPoint를 부를 수 있다.
     */
    private MockMvc chain(AuthorizationManager<HttpServletRequest> authorizationManager) {
        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addFilters(
                        new JwtAuthenticationFilter(jwtUtil, authService),
                        new AnonymousAuthenticationFilter("test-anonymous-key"),
                        new ExceptionTranslationFilter(new CustomEntryPoint()),
                        new AuthorizationFilter(authorizationManager))
                .build();
    }

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, AT_VALIDITY, RT_VALIDITY);
        secured = chain(AuthenticatedAuthorizationManager.authenticated());
    }

    @AfterEach
    void tearDown() {
        // SecurityContextHolder는 스레드 로컬이라 테스트 간에 인증 상태가 새어나간다.
        SecurityContextHolder.clearContext();
    }

    private String validAccessToken() {
        return jwtUtil.createAccessToken(MEMBER_ID, "USER", "강민");
    }

    @Nested
    @DisplayName("인증이 필요한 경로")
    class SecuredPath {

        @Test
        @DisplayName("유효한 AT면 통과하고 인증 객체가 컨트롤러까지 전달된다")
        void passesWithValidToken() throws Exception {
            secured.perform(get("/api/probe").header("Authorization", "Bearer " + validAccessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isSuccess").value(true))
                    .andExpect(jsonPath("$.result").value((int) MEMBER_ID));
        }

        @Test
        @DisplayName("토큰이 없으면 401 COMMON401_1이다")
        void rejectsMissingToken() throws Exception {
            // 필터는 아무 attribute도 남기지 않는다. EntryPoint가 기본값으로 떨어뜨린다.
            secured.perform(get("/api/probe"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("COMMON401_1"));
        }

        @Test
        @DisplayName("Bearer 접두어가 없으면 토큰으로 인식하지 않는다")
        void ignoresTokenWithoutBearerPrefix() throws Exception {
            secured.perform(get("/api/probe").header("Authorization", validAccessToken()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("COMMON401_1"));
        }

        @Test
        @DisplayName("만료된 AT는 401 AUTH401_1이다")
        void mapsExpiredToken() throws Exception {
            // 프론트가 이 코드를 보고 /api/auth/reissue를 호출한다.
            // 무효 토큰과 같은 코드로 뭉개지면 무한 재발급 루프에 빠진다.
            String expired = new JwtUtil(SECRET, -120_000L, RT_VALIDITY).createAccessToken(MEMBER_ID, "USER", "강민");

            secured.perform(get("/api/probe").header("Authorization", "Bearer " + expired))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH401_1"))
                    .andExpect(jsonPath("$.message").value("만료된 토큰입니다."));
        }

        @Test
        @DisplayName("다른 키로 서명된 AT는 401 AUTH401_2다")
        void mapsForgedSignature() throws Exception {
            String forged = new JwtUtil(OTHER_SECRET, AT_VALIDITY, RT_VALIDITY)
                    .createAccessToken(MEMBER_ID, "USER", "강민");

            secured.perform(get("/api/probe").header("Authorization", "Bearer " + forged))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH401_2"));
        }

        @Test
        @DisplayName("JWT 형식이 아니면 401 AUTH401_2다")
        void mapsMalformedToken() throws Exception {
            secured.perform(get("/api/probe").header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH401_2"));
        }

        @Test
        @DisplayName("RT를 AT 자리에 넣으면 401 AUTH401_2다")
        void rejectsRefreshTokenAsAccessToken() throws Exception {
            // 서명도 만료도 멀쩡하다. typ 클레임을 보지 않으면 14일짜리 RT가 AT로 통용된다.
            String refreshToken = jwtUtil.createRefreshToken(MEMBER_ID);

            secured.perform(get("/api/probe").header("Authorization", "Bearer " + refreshToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH401_2"));
        }

        @Test
        @DisplayName("로그아웃한 AT는 서명이 멀쩡해도 401 AUTH401_5다")
        void rejectsBlacklistedToken() throws Exception {
            // 로그아웃 직후 같은 AT를 그대로 재사용하는 시나리오.
            // AT는 stateless라 서명/만료만 보면 통과하므로, 블랙리스트 조회가 유일한 방어선이다.
            given(authService.isBlacklisted(anyString())).willReturn(true);

            secured.perform(get("/api/probe").header("Authorization", "Bearer " + validAccessToken()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH401_5"))
                    .andExpect(jsonPath("$.message").value("블랙리스트에 등록된 AT입니다."));
        }

        @Test
        @DisplayName("블랙리스트 조회가 실패하면 통과시키지 않고 401 AUTH401_3이다")
        void failsClosedWhenBlacklistLookupBreaks() throws Exception {
            // DB 장애로 블랙리스트를 못 읽는 상황. 여기서 통과시키면 로그아웃된 토큰이 되살아난다.
            given(authService.isBlacklisted(anyString())).willThrow(new IllegalStateException("DB 연결 실패"));

            secured.perform(get("/api/probe").header("Authorization", "Bearer " + validAccessToken()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH401_3"));
        }

        @Test
        @DisplayName("필터 예외는 500 HTML이 아니라 JSON 401로 나간다")
        void alwaysRendersJson() throws Exception {
            // 필터 안에서 던진 예외는 @RestControllerAdvice가 잡지 못한다.
            // 필터가 예외를 밖으로 흘리면 컨테이너 기본 에러 페이지(HTML)가 나가버린다.
            given(authService.isBlacklisted(anyString())).willThrow(new IllegalStateException("DB 연결 실패"));

            secured.perform(get("/api/probe").header("Authorization", "Bearer " + validAccessToken()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.result").exists());
        }
    }

    @Nested
    @DisplayName("permitAll 경로")
    class PublicPath {

        @Test
        @DisplayName("무효한 AT가 와도 익명으로 정상 처리된다")
        void proceedsAnonymouslyWithBadToken() throws Exception {
            // 필터는 실패 사유를 attribute에 남기지만 응답을 쓰지 않는다.
            // AuthorizationFilter가 막지 않으면 attribute는 그대로 폐기되고 요청은 성공한다.
            // /api/auth/reissue가 만료된 AT를 들고 와도 동작해야 하는 이유다.
            MockMvc open = chain((authentication, object) -> new AuthorizationDecision(true));

            open.perform(get("/api/probe").header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").doesNotExist());
        }
    }

    @Nested
    @DisplayName("체인 구성 전제")
    class ChainAssumptions {

        @Test
        @DisplayName("미인증 요청은 403이 아니라 401로 떨어진다")
        void anonymousGets401NotForbidden() throws Exception {
            // AuthorizationFilter가 던지는 AuthorizationDeniedException은 AccessDeniedException이다.
            // ExceptionTranslationFilter가 "익명이면 401(EntryPoint), 인증됐으면 403(AccessDeniedHandler)"로
            // 갈라주기 때문에 토큰 없는 요청이 403이 아니라 401을 받는다.
            secured.perform(get("/api/probe"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
