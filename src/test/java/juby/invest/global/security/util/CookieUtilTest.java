package juby.invest.global.security.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CookieUtil (RT 쿠키 생성 / 삭제)")
class CookieUtilTest {

    private static final long RT_VALIDITY = 1_209_600_000L; // 14일

    /** @Value로 주입받는 필드를 테스트에서 직접 채워 넣는다. */
    private CookieUtil cookieUtil(boolean secure, String sameSite) {
        CookieUtil cookieUtil = new CookieUtil();
        ReflectionTestUtils.setField(cookieUtil, "rtValidity", RT_VALIDITY);
        ReflectionTestUtils.setField(cookieUtil, "secure", secure);
        ReflectionTestUtils.setField(cookieUtil, "sameSite", sameSite);
        return cookieUtil;
    }

    /** "refreshToken=abc; Path=/api/auth; ..." 에서 속성 하나를 꺼낸다. */
    private static String attribute(String setCookie, String name) {
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, name + "=", 0, name.length() + 1)) {
                return trimmed.substring(name.length() + 1);
            }
        }
        return null;
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("RT를 HttpOnly 쿠키로 내려준다")
        void setsHttpOnly() {
            String cookie = cookieUtil(false, "Lax").createRTCookie("my-refresh-token");

            assertThat(attribute(cookie, "refreshToken")).isEqualTo("my-refresh-token");
            // HttpOnly가 빠지면 XSS 한 번에 14일짜리 RT가 통째로 털린다.
            assertThat(cookie).containsIgnoringCase("HttpOnly");
        }

        @Test
        @DisplayName("Path를 /api/auth로 제한한다")
        void limitsPath() {
            String cookie = cookieUtil(false, "Lax").createRTCookie("my-refresh-token");

            // 로그인/재발급/로그아웃 외의 모든 요청에 RT가 실려 나가지 않도록 범위를 좁힌다.
            assertThat(attribute(cookie, "Path")).isEqualTo("/api/auth");
        }

        @Test
        @DisplayName("Max-Age를 RT 유효기간(초)으로 맞춘다")
        void setsMaxAgeFromValidity() {
            String cookie = cookieUtil(false, "Lax").createRTCookie("my-refresh-token");

            // 프로퍼티는 밀리초 단위인데 쿠키 Max-Age는 초 단위다. 환산을 빠뜨리면 14일이 3.8년이 된다.
            assertThat(attribute(cookie, "Max-Age")).isEqualTo(String.valueOf(RT_VALIDITY / 1000));
        }

        @Test
        @DisplayName("설정에 따라 Secure / SameSite가 바뀐다")
        void reflectsConfiguredAttributes() {
            String crossSite = cookieUtil(true, "None").createRTCookie("my-refresh-token");

            // HTTPS 전환 시 코드 수정 없이 프로퍼티만으로 크로스 사이트 조합이 나와야 한다.
            assertThat(crossSite).containsIgnoringCase("Secure");
            assertThat(attribute(crossSite, "SameSite")).isEqualTo("None");

            String sameSite = cookieUtil(false, "Lax").createRTCookie("my-refresh-token");

            assertThat(sameSite).doesNotContainIgnoringCase("Secure");
            assertThat(attribute(sameSite, "SameSite")).isEqualTo("Lax");
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("Max-Age를 0으로 만들고 값을 비운다")
        void expiresImmediately() {
            String cookie = cookieUtil(false, "Lax").deleteRTCookie();

            assertThat(attribute(cookie, "Max-Age")).isEqualTo("0");
            assertThat(cookie).startsWith("refreshToken=;");
        }

        @Test
        @DisplayName("생성 쿠키와 이름 / Path가 완전히 같다")
        void matchesCreatedCookieIdentity() {
            CookieUtil cookieUtil = cookieUtil(true, "None");
            String created = cookieUtil.createRTCookie("my-refresh-token");
            String deleted = cookieUtil.deleteRTCookie();

            // 브라우저는 (이름, 도메인, Path)로 쿠키를 식별한다.
            // Path가 한 글자만 달라도 삭제가 조용히 실패하고 예전 RT가 그대로 남는다.
            assertThat(attribute(deleted, "Path")).isEqualTo(attribute(created, "Path"));
            assertThat(deleted).startsWith("refreshToken=");
            assertThat(created).startsWith("refreshToken=");
        }
    }

    @Nested
    @DisplayName("기동 시 설정 검증")
    class Validation {

        @Test
        @DisplayName("잘못된 조합이어도 예외를 던지지 않는다")
        void neverBlocksStartup() {
            // SameSite=None + Secure=false는 브라우저가 쿠키를 버리는 조합이지만,
            // 애플리케이션을 못 뜨게 만들 일은 아니다. 경고 로그로만 남긴다.
            CookieUtil cookieUtil = cookieUtil(false, "None");

            cookieUtil.validate();

            assertThat(cookieUtil.createRTCookie("my-refresh-token")).isNotBlank();
        }
    }
}