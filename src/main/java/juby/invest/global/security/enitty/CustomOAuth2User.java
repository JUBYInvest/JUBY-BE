package juby.invest.global.security.enitty;

import juby.invest.domain.member.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Getter
@AllArgsConstructor
public class CustomOAuth2User implements OAuth2User {

    private final Long userId;
    private final Role role;
    private final String name;

    @Override
    public Map<String, Object> getAttributes() {
        return null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        Collection<GrantedAuthority> collection = new ArrayList<>();
        collection.add(new GrantedAuthority() {
            @Override
            public String getAuthority() {
                return "ROLE_" + role.name();
            }
        });
        return collection;
    }
}
