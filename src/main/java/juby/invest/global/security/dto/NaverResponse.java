package juby.invest.global.security.dto;

import juby.invest.domain.member.enums.SocialType;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class NaverResponse implements OAuth2Response{

    private final Map<String, Object> attribute;

    @Override
    public SocialType getProvider() {
        return SocialType.NAVER;
    }

    @Override
    public String getProviderId() {
        return attribute.get("id").toString();
    }

    @Override
    public String getEmail() {
        return attribute.get("email").toString();
    }

    @Override
    public String getName() {
        return attribute.get("name").toString();
    }

    @Override
    public String getProfileUrl() {
        return attribute.get("profile_image").toString();
    }

    @Override
    public String getBirthday() {
        return attribute.get("birthday").toString();
    }

    @Override
    public String getBirthyear() {
        return attribute.get("birthyear").toString();
    }
}
