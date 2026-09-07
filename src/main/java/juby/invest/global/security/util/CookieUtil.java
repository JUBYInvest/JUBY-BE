package juby.invest.global.security.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    @Value("${jwt.refresh-token-validity}") private long rtValidity;

    /***
     * 함수 기능: RT 쿠키를 생성한다.
     * @param refreshToken RT
     * @return RT 쿠키
     */
    public String createRTCookie(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .maxAge(rtValidity/1000)
                .path("/")
                .secure(false)
                .sameSite("Lax")
                .httpOnly(true)
                .build()
                .toString();
    }
}
