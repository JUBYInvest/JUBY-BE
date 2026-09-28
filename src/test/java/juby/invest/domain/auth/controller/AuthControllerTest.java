package juby.invest.domain.auth.controller;

import jakarta.servlet.http.Cookie;
import juby.invest.domain.auth.dto.ReissueDto;
import juby.invest.domain.auth.exception.AuthException;
import juby.invest.domain.auth.exception.code.AuthErrorCode;
import juby.invest.domain.auth.service.AuthService;
import juby.invest.domain.member.enums.Role;
import juby.invest.global.apiPayload.handler.GeneralExceptionAdvice;
import juby.invest.global.security.entity.CustomOAuth2User;
import juby.invest.global.security.util.CookieUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("인증 API (POST /api/auth/reissue, /logout)")
class AuthControllerTest {

    private static final long MEMBER_ID = 1L;

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // CookieUtil은 모킹하지 않는다. 실제로 나가는 Set-Cookie 헤더까지 확인해야 의미가 있다.
        CookieUtil cookieUtil = new CookieUtil();
        ReflectionTestUtils.setField(cookieUtil, "rtValidity", 1_209_600_000L);
        ReflectionTestUtils.setField(cookieUtil, "secure", false);
        ReflectionTestUtils.setField(cookieUtil, "sameSite", "Lax");

        // 시큐리티 필터 없이 컨트롤러만 올린다. @AuthenticationPrincipal만 리졸버로 따로 붙인다.
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, cookieUtil))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GeneralExceptionAdvice())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void login(CustomOAuth2User principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }

    @Nested
    @DisplayName("재발급")
    class Reissue {

        @Test
        @DisplayName("새 AT는 바디로, 새 RT는 쿠키로 내려준다")
        void returnsAccessTokenInBodyAndRefreshTokenInCookie() throws Exception {
            given(authService.reissue("old-rt")).willReturn(new ReissueDto.ReissueRes("new-at", "new-rt"));

            MvcResult result = mockMvc.perform(post("/api/auth/reissue").cookie(new Cookie("refreshToken", "old-rt")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isSuccess").value(true))
                    .andExpect(jsonPath("$.code").value("AUTH200_1"))
                    .andExpect(jsonPath("$.result.accessToken").value("new-at"))
                    .andReturn();

            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).startsWith("refreshToken=new-rt");
            assertThat(setCookie).containsIgnoringCase("HttpOnly");
            assertThat(setCookie).contains("Path=/api/auth");
        }

        @Test
        @DisplayName("RT 원문을 응답 바디에 노출하지 않는다")
        void neverLeaksRefreshTokenInBody() throws Exception {
            given(authService.reissue("old-rt")).willReturn(new ReissueDto.ReissueRes("new-at", "new-rt"));

            MvcResult result = mockMvc.perform(post("/api/auth/reissue").cookie(new Cookie("refreshToken", "old-rt")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.refreshToken").doesNotExist())
                    .andReturn();

            // 바디에 실리면 프론트가 localStorage에 담을 수 있다. RT는 HttpOnly 쿠키로만 존재해야 한다.
            assertThat(result.getResponse().getContentAsString()).doesNotContain("new-rt");
        }

        @Test
        @DisplayName("RT 쿠키가 없어도 400이 아니라 서비스까지 전달된다")
        void passesNullWhenCookieMissing() throws Exception {
            willThrow(new AuthException(AuthErrorCode.RT_COOKIE_MISSING)).given(authService).reissue(any());

            mockMvc.perform(post("/api/auth/reissue"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH401_6"));

            // @CookieValue의 기본값(required=true)이면 여기서 MissingRequestCookieException이 터져
            // 400이 나가고, 프론트의 재로그인 트리거(401)를 비껴간다.
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            then(authService).should().reissue(captor.capture());
            assertThat(captor.getValue()).isNull();
        }

        @Test
        @DisplayName("만료된 RT는 401 AUTH401_1로 응답한다")
        void mapsExpiredTokenTo401() throws Exception {
            willThrow(new AuthException(AuthErrorCode.EXPIRED_TOKEN)).given(authService).reissue(any());

            mockMvc.perform(post("/api/auth/reissue").cookie(new Cookie("refreshToken", "expired-rt")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("AUTH401_1"));
        }

        @Test
        @DisplayName("불일치 RT는 401 AUTH401_2로 응답한다")
        void mapsInvalidTokenTo401() throws Exception {
            willThrow(new AuthException(AuthErrorCode.INVALID_TOKEN)).given(authService).reissue(any());

            mockMvc.perform(post("/api/auth/reissue").cookie(new Cookie("refreshToken", "forged-rt")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH401_2"));
        }

        @Test
        @DisplayName("재발급에 실패하면 RT 쿠키를 새로 내려주지 않는다")
        void doesNotSetCookieOnFailure() throws Exception {
            willThrow(new AuthException(AuthErrorCode.INVALID_TOKEN)).given(authService).reissue(any());

            MvcResult result = mockMvc.perform(post("/api/auth/reissue").cookie(new Cookie("refreshToken", "forged-rt")))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("인증 객체를 그대로 서비스에 넘긴다")
        void delegatesPrincipal() throws Exception {
            CustomOAuth2User principal =
                    new CustomOAuth2User(MEMBER_ID, Role.USER, "강민", "jti-1234", LocalDateTime.now().plusMinutes(30));
            login(principal);

            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("AUTH200_2"));

            // 컨트롤러가 AT를 다시 파싱하지 않고, 필터가 이미 뽑아 둔 jti를 그대로 쓴다.
            ArgumentCaptor<CustomOAuth2User> captor = ArgumentCaptor.forClass(CustomOAuth2User.class);
            then(authService).should().logout(captor.capture());
            assertThat(captor.getValue().getJti()).isEqualTo("jti-1234");
            assertThat(captor.getValue().getId()).isEqualTo(MEMBER_ID);
        }

        @Test
        @DisplayName("RT 쿠키를 만료시킨다")
        void expiresRefreshTokenCookie() throws Exception {
            login(new CustomOAuth2User(MEMBER_ID, Role.USER, "강민", "jti-1234", LocalDateTime.now().plusMinutes(30)));

            MvcResult result = mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andReturn();

            // 서버에서 RT row를 지워도 브라우저에 쿠키가 남으면 매 요청에 죽은 RT가 실려 나간다.
            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).startsWith("refreshToken=;");
            assertThat(setCookie).contains("Max-Age=0");
            assertThat(setCookie).contains("Path=/api/auth");
        }
    }
}