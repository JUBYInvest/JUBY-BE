package juby.invest.global.security.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import juby.invest.global.security.entity.CustomOAuth2User;
import juby.invest.domain.member.enums.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessTokenValidity;
    private final long refreshTokenValidity;

    public JwtUtil(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.access-token-validity}") long accessTokenValidity,
            @Value("${jwt.refresh-token-validity}") long refreshTokenValidity) {
        this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = accessTokenValidity;
        this.refreshTokenValidity = refreshTokenValidity;
    }

    public String createAccessToken(Long userId, String role, String name){

        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenValidity);
        String jti = UUID.randomUUID().toString(); // JWT ID

        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim("typ", "access")
                .claim("role", role)
                .claim("name", name)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(Long userId){

        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshTokenValidity);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("typ", "refresh")
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /***
     * 함수 기능: 토큰의 유효성을 검사하고 Claims를 추출한다.
     * @param token JWT
     * @return Claims
     */
    public Claims parseClaims(String token){
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .clockSkewSeconds(60)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
    }

    /***
     * 함수 기능: 토큰의 payload에서 claims를 꺼내어 인증 객체를 조립한다.
     * @param claims JWT Payload의 Claim들
     * @return Authentication 인증객체
     */
    public Authentication getAuthentication(Claims claims){

        Long userId = Long.parseLong(claims.getSubject());
        Role role = Role.valueOf(claims.get("role", String.class));
        String name = claims.get("name", String.class);

        CustomOAuth2User principal = new CustomOAuth2User(userId, role, name);
        return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
    }
}
