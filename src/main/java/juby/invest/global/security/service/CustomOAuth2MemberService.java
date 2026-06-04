package juby.invest.global.security.service;

import jakarta.transaction.Transactional;
import juby.invest.global.security.dto.KakaoResponse;
import juby.invest.global.security.entity.CustomOAuth2User;
import juby.invest.global.security.dto.GoogleResponse;
import juby.invest.global.security.dto.NaverResponse;
import juby.invest.global.security.dto.OAuth2Response;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.enums.Role;
import juby.invest.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2MemberService extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository;

    /***
     * Resource Server로 부터 받은 userRequest를 OAuth2Response DTO로 변환,
     * 얻은 회원 정보를 DB에 저장하는 함수.
     * @param userRequest AccessToken과 Client 설정 정보
     * @return OAuth2User
     * @throws OAuth2AuthenticationException
     */
    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest);
        log.info("oAuth2User = {} ", oAuth2User.getAttributes());

        String registrationId = userRequest.getClientRegistration().getRegistrationId(); // Returns the identifier for the registration
        OAuth2Response oAuth2Response;

        if (registrationId.equals("google")){
            oAuth2Response = new GoogleResponse(oAuth2User.getAttributes());
        }
        else if (registrationId.equals("naver")){
            oAuth2Response = new NaverResponse(oAuth2User.getAttribute("response"));
        }
        else if (registrationId.equals("kakao")){
            String providerId = String.valueOf((Long)oAuth2User.getAttribute("id"));
            Map<String, Object> attributes = oAuth2User.getAttribute("kakao_account");
            Map<String, Object> profile = (Map<String, Object>) attributes.get("profile");
            oAuth2Response = new KakaoResponse(providerId, attributes.get("email").toString(), profile.get("nickname").toString());
        }
        else {
            throw new OAuth2AuthenticationException("해당 provider는 존재하지 않습니다." + registrationId);
        }

        // 기존 회원이 아니면 DB에 추가
        Member member = memberRepository.findBySocialTypeAndProviderId(oAuth2Response.getProvider(), oAuth2Response.getProviderId())
                .orElseGet(() -> memberRepository.save(Member.builder()
                        .email(oAuth2Response.getEmail())
                        .name(oAuth2Response.getName())
                        .providerId(oAuth2Response.getProviderId())
                        .socialType(oAuth2Response.getProvider())
                        .role(Role.USER)
                        .birth(oAuth2Response.getBirthday())
                        .build()));

        log.info("로그인 혹은 DB에 새로 추가된 Member: {}", member);

        return new CustomOAuth2User(member.getId(), member.getRole(), member.getName());
    }
}
