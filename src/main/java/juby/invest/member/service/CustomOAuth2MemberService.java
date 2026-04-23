package juby.invest.member.service;

import jakarta.transaction.Transactional;
import juby.invest.member.dto.CustomOAuth2User;
import juby.invest.member.dto.GoogleResponse;
import juby.invest.member.dto.NaverResponse;
import juby.invest.member.dto.OAuth2Response;
import juby.invest.member.entity.Member;
import juby.invest.member.entity.SocialAccount;
import juby.invest.member.enums.Role;
import juby.invest.member.repository.MemberRepository;
import juby.invest.member.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final SocialAccountRepository socialAccountRepository;

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
        else {
            throw new OAuth2AuthenticationException("해당 provider는 존재하지 않습니다." + registrationId);
        }

        Member member = memberRepository.findByEmail(oAuth2Response.getEmail());

        // 첫 회원 가입의 경우 (DB에 해당 회원의 이메일이 존재하지 않을 경우)
        if(member == null){
            log.info("첫 회원 가입 성공!");
            member = memberRepository.save(Member.builder()
                    .birth(oAuth2Response.getBirthday())
                    .email(oAuth2Response.getEmail())
                    .nickName(oAuth2Response.getName())
                    .profileImg(oAuth2Response.getProfileUrl())
                    .role(Role.USER)
                    .build());
            socialAccountRepository.save(SocialAccount.builder()
                    .member(member)
                    .provider(oAuth2Response.getProvider())
                    .provider_id(oAuth2Response.getProviderId())
                    .build());
        }
        else { // DB에 이미 해당 회원의 이메일이 존재할 경우
            // 기존과 다른 소셜로그인을 통해 로그인 했을 경우
            if (!socialAccountRepository.existsByMemberAndProvider(member, oAuth2Response.getProvider())){
                log.info("이미 해당 이메일이 존재하기에 Social Account에 provider와 providerId를 추가합니다.");
                socialAccountRepository.save(SocialAccount.builder()
                        .member(member)
                        .provider(oAuth2Response.getProvider())
                        .provider_id(oAuth2Response.getProviderId())
                        .build());
            }
            else{
                log.info("이미 해당 이메일과 해당 provider, providerId가 존재합니다.");
            }
        }

        return new CustomOAuth2User(member.getId(), member.getRole(), member.getNickName());
    }
}
