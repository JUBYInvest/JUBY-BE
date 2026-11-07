package juby.invest.domain.livechat.config;

import juby.invest.domain.auth.exception.AuthException;
import juby.invest.domain.auth.exception.code.AuthErrorCode;
import juby.invest.domain.auth.service.AuthService;
import juby.invest.global.apiPayload.exception.ProjectException;
import juby.invest.global.security.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("LiveChatChannelInterceptor 인증")
class LiveChatChannelInterceptorTest {

    private static final String SECRET = "juby-invest-test-secret-key-must-be-at-least-32-bytes-long";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 1_800_000, 1_209_600_000);
    private final AuthService authService = mock(AuthService.class);
    private final LiveChatChannelInterceptor interceptor = new LiveChatChannelInterceptor(jwtUtil, authService);

    private AuthErrorCode errorCodeOf(Throwable e) {
        return (AuthErrorCode) ((ProjectException) e).getErrorCode();
    }

    @Test
    @DisplayName("토큰이 없으면 비로그인 세션이 된다")
    void noTokenIsGuest() {
        LiveChatPrincipal principal = interceptor.authenticate(null);

        assertThat(principal.isGuest()).isTrue();
        assertThat(principal.getName()).startsWith("guest-");
    }

    @Test
    @DisplayName("유효한 토큰이면 회원 ID를 이름으로 쓰는 회원 세션이 된다")
    void validTokenIsMember() {
        String token = jwtUtil.createAccessToken(7L, "USER", "실명");

        LiveChatPrincipal principal = interceptor.authenticate("Bearer " + token);

        assertThat(principal.isGuest()).isFalse();
        assertThat(principal.memberId()).isEqualTo(7L);
        // 실명은 겹칠 수 있어 user 목적지 키로 쓰면 남의 에러를 받게 된다.
        assertThat(principal.getName()).isEqualTo("7");
        assertThat(principal.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("만료된 토큰은 AUTH401_1로 거부한다")
    void expiredToken() {
        // clockSkew(60초)보다 더 지난 토큰
        JwtUtil expiredIssuer = new JwtUtil(SECRET, -120_000, 1_209_600_000);
        String token = expiredIssuer.createAccessToken(7L, "USER", "실명");

        assertThatThrownBy(() -> interceptor.authenticate("Bearer " + token))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(AuthErrorCode.EXPIRED_TOKEN));
    }

    @Test
    @DisplayName("로그아웃/탈퇴로 블랙리스트에 오른 토큰은 거부한다")
    void blacklistedToken() {
        given(authService.isBlacklisted(anyString())).willReturn(true);
        String token = jwtUtil.createAccessToken(7L, "USER", "실명");

        assertThatThrownBy(() -> interceptor.authenticate("Bearer " + token))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(AuthErrorCode.TOKEN_BLACKLISTED));
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 거부한다")
    void notBearer() {
        assertThatThrownBy(() -> interceptor.authenticate("Token abc"))
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(AuthErrorCode.INVALID_TOKEN));
    }
}
