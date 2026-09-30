package juby.invest.global.security.entity;

import juby.invest.domain.member.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Getter
@AllArgsConstructor
public class CustomOAuth2User implements OAuth2User {

    // jti와 expiresAt은 JWT 인증으로 만들어진 principal에만 존재.
    // 즉, 소셜 로그인 완료 후, 프론트로 리다이렉트 되는 경우에는 null이 들어가게 된다.
    private final Long id;
    private final Role role;
    private final String name;
    private final String jti;
    private final LocalDateTime expiresAt;

    @Override
    public Map<String, Object> getAttributes() {
        return null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        Collection<GrantedAuthority> collection = new ArrayList<>();
        collection.add((GrantedAuthority) () -> "ROLE_" + role.name());
        return collection;
    }
}
