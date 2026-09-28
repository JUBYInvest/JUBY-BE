package juby.invest.global.security.dto;

import juby.invest.domain.member.enums.SocialType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class KakaoResponse implements OAuth2Response{

    // 카카오 고유 id
    private final String providerId;
    private final String email;
    private final String name;

    @Override
    public SocialType getProvider() {
        return SocialType.KAKAO;
    }

    @Override
    public String getProviderId() {
        return providerId;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getProfileUrl() {
        return "null";
    }

    @Override
    public String getBirthday() {
        return "null";
    }

    @Override
    public String getBirthyear() {
        return "null";
    }
}
