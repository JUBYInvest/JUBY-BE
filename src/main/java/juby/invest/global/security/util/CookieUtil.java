package juby.invest.global.security.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CookieUtil {

    private static final String RT_COOKIE_NAME = "refreshToken";
    private static final String RT_COOKIE_PATH = "/api/auth";

    @Value("${jwt.refresh-token-validity}") private long rtValidity;
    @Value("${app.cookie.secure}") private boolean secure;
    @Value("${app.cookie.same-site}") private String sameSite;

    /***
     * 함수 기능: RT 쿠키를 생성한다.
     * @param refreshToken RT
     * @return RT 쿠키
     */
    public String createRTCookie(String refreshToken) {
        return buildRTCookie(refreshToken, rtValidity/1000);
    }

    /***
     * 함수 기능: RT 쿠키를 삭제한다. 값을 비우고 Max-Age를 0으로 만든다.
     * @return 삭제용 RT 쿠키
     */
    public String deleteRTCookie() {
        return buildRTCookie("", 0);
    }

    /***
     * 함수 기능: RT 쿠키의 공통 속성을 세팅한다.
     *          삭제 쿠키는 생성 시와 이름/Path/속성이 모두 같아야 브라우저가 같은 쿠키로 인식해 지운다.
     *          한쪽만 수정하면 삭제가 조용히 실패하므로 반드시 한 곳에서 관리한다.
     * @param value 쿠키 값 (삭제 시 빈 문자열)
     * @param maxAge 만료까지 남은 초 (삭제 시 0)
     * @return RT 쿠키
     */
    private String buildRTCookie(String value, long maxAge) {
        return ResponseCookie.from(RT_COOKIE_NAME, value)
                .maxAge(maxAge)
                .path(RT_COOKIE_PATH)
                .secure(secure)
                .sameSite(sameSite)
                .httpOnly(true)
                .build()
                .toString();
    }
}
