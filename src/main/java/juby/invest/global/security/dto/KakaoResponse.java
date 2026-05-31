package juby.invest.global.security.dto;

import juby.invest.domain.member.enums.SocialType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class KakaoResponse implements OAuth2Response{

    // 카카오 고유 id
    private final String providerId;
    private final String email;
    private final String name;
    // kakao_account={profile_nickname_needs_agreement=false,
    // profile_image_needs_agreement=true,
    // profile={nickname=김강민, is_default_nickname=false},
    // has_email=true, email_needs_agreement=false, is_email_valid=true, is_email_verified=true,
    // email=applejuice0409@naver.com}

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
}
