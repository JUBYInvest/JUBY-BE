package juby.invest.domain.member.dto;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public interface OAuth2Response {
    String getProvider(); // ex) google, naver, kakao
    String getProviderId(); // google이 Resource Owner에게 부여한 고유 회원 번호
    String getEmail(); // 사용자 이메일
    String getName(); // 사용자 이름
    String getProfileUrl();
    String getBirthday();
}
