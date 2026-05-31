package juby.invest.global.security.dto;

import juby.invest.domain.member.enums.SocialType;
import org.springframework.stereotype.Component;

@Component
public interface OAuth2Response {
    SocialType getProvider(); // ex) google, naver, kakao
    String getProviderId(); // google이 Resource Owner에게 부여한 고유 회원 번호
    String getEmail(); // 사용자 이메일
    String getName(); // 사용자 이름
    String getProfileUrl();
    String getBirthday();
}
