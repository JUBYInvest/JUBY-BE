package juby.invest.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인증 없이 열려 있어야 하는 경로를 못으로 박는다.
 * <p>
 * 과거에 allowUris가 `/api/**`에서 개별 경로로 좁혀졌는데(커밋 1cd2d4b), 그때 목록에서 빠진 공개
 * 엔드포인트는 <b>컴파일도 테스트도 통과하면서</b> 401만 돌려준다. 공개 시장 데이터는 로그인과
 * 무관하게 보여야 하므로, 어느 경로가 공개인지를 테스트로 남긴다.
 * <p>
 * 특히 SSE 스트림은 브라우저 EventSource가 커스텀 헤더를 보낼 수 없어, 허용 목록에서 빠지면
 * <b>로그인한 사용자도 연결할 방법이 없다.</b>
 */
@DisplayName("보안 허용 경로")
class SecurityConfigAllowUriTest {

    private final AntPathMatcher matcher = new AntPathMatcher();

    private boolean isAllowed(String uri) {
        return Arrays.stream(SecurityConfig.allowUris)
                .anyMatch(pattern -> matcher.match(pattern, uri));
    }

    @DisplayName("공개 시장 데이터 경로는 인증 없이 열려 있다")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api/stocks",                  // 종목 목록
            "/api/stocks/005930",           // 종목 상세
            "/api/market-indices",          // 코스피/코스닥 지수
            "/api/stream/prices",           // 전 종목 실시간 스트림
            "/api/stream/prices/005930"     // 종목 1건 실시간 스트림
    })
    void allowsPublicMarketDataUris(String uri) {
        assertThat(isAllowed(uri))
                .as("%s 가 allowUris에서 빠지면 비로그인 사용자에게 401이 나간다", uri)
                .isTrue();
    }

    @DisplayName("회원 전용 경로는 열려 있지 않다")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api/members/like-stocks",     // 관심 종목
            "/api/chat-sessions",           // AI 챗봇 대화
            "/api/backtest/presets"         // 백테스트
    })
    void doesNotAllowMemberOnlyUris(String uri) {
        assertThat(isAllowed(uri))
                .as("%s 가 allowUris에 들어가면 인증 없이 회원 데이터에 접근하게 된다", uri)
                .isFalse();
    }

    @Test
    @DisplayName("토큰 재발급만 열고 다른 인증 경로는 열지 않는다")
    void allowsOnlyReissueAmongAuthUris() {

        // 재발급은 만료된 AT로도 호출해야 하므로 공개다. 로그아웃/탈퇴까지 열리면 안 된다.
        assertThat(isAllowed("/api/auth/reissue")).isTrue();
        assertThat(isAllowed("/api/auth/logout")).isFalse();
    }
}