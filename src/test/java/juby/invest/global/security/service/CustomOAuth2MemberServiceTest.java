package juby.invest.global.security.service;

import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.enums.Role;
import juby.invest.domain.member.enums.SocialType;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.global.security.dto.KakaoResponse;
import juby.invest.global.security.entity.CustomOAuth2User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestOperations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 카카오 소셜 로그인으로 만들어진 KakaoResponse가 parseBirth(birthyear, birthday)를 통과해
 * DB까지 저장되는지 확인한다.
 * <p>
 * 카카오는 생일 수집 동의 항목이 없어 KakaoResponse의 birthyear/birthday가 항상 null이다.
 * 이 값이 parseBirth의 null 가드에 걸리지 않으면 "null-null" 같은 문자열이 LocalDate.parse로 들어가
 * DateTimeParseException이 터지고, loadUser의 catch가 이를 OAuth2AuthenticationException으로
 * 바꿔버려 "회원 가입 자체가 실패"한다. 즉 생일 하나 때문에 로그인이 막히는 경로라서,
 * parseBirth만 단위 테스트하지 않고 loadUser → Member 저장 → DB flush까지 실제로 태운다.
 * <p>
 * super.loadUser()가 카카오 서버를 실제로 호출하므로, DefaultOAuth2UserService의
 * RestOperations를 목으로 갈아끼워 userinfo 응답만 흉내낸다.
 */
@DataJpaTest
@DisplayName("CustomOAuth2MemberService (소셜 로그인 회원 저장)")
class CustomOAuth2MemberServiceTest {

    private static final long KAKAO_ID = 1234567890L;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MemberRepository memberRepository;

    private CustomOAuth2MemberService memberService;
    private RestOperations restOperations;

    @BeforeEach
    void setUp() {
        restOperations = mock(RestOperations.class);
        memberService = new CustomOAuth2MemberService(memberRepository);
        memberService.setRestOperations(restOperations);
    }

    /** registrationId와 user-name-attribute는 application.yaml의 실제 설정과 같게 맞춘다. */
    private static ClientRegistration registration(String registrationId, String userNameAttribute) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .redirectUri("http://localhost:8080/login/oauth2/code/" + registrationId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://example.com/oauth/authorize")
                .tokenUri("https://example.com/oauth/token")
                .userInfoUri("https://example.com/userinfo")
                .userNameAttributeName(userNameAttribute)
                .build();
    }

    private static OAuth2UserRequest userRequest(ClientRegistration registration) {
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "test-access-token",
                Instant.now(), Instant.now().plusSeconds(3600));
        return new OAuth2UserRequest(registration, accessToken);
    }

    /** Resource Server의 userinfo 응답을 목 RestOperations로 흘려준다. */
    private void givenUserInfoResponse(Map<String, Object> body) {
        given(restOperations.exchange(
                any(RequestEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .willReturn(ResponseEntity.ok(body));
    }

    /**
     * 카카오 userinfo 응답 구조. 서비스가 id를 (Long)으로 캐스팅하고
     * kakao_account.profile.nickname을 읽으므로 그 모양 그대로 만든다.
     * 생일 관련 필드는 카카오가 내려주지 않으니 아예 넣지 않는다.
     */
    private static Map<String, Object> kakaoUserInfo(String email, String nickname) {
        return Map.of(
                "id", KAKAO_ID,
                "kakao_account", Map.of(
                        "email", email,
                        "profile", Map.of("nickname", nickname)));
    }

    @Nested
    @DisplayName("카카오 로그인 (생일 정보 없음)")
    class KakaoLogin {

        @Test
        @DisplayName("birthyear/birthday가 null이면 예외 없이 birth = null로 저장된다")
        void savesMemberWithNullBirth() {
            givenUserInfoResponse(kakaoUserInfo("kakao@test.com", "강민"));

            OAuth2User principal = memberService.loadUser(userRequest(registration("kakao", "id")));

            // flush로 실제 INSERT를 내보내고, clear로 1차 캐시를 비워 DB에서 다시 읽는다.
            // flush 없이 단정하면 영속성 컨텍스트만 보고 통과해버려 "DB에 잘 들어갔는지"를 못 본다.
            em.flush();
            em.clear();

            Member saved = memberRepository.findBySocialTypeAndProviderIdAndDeletedAtIsNull(
                            SocialType.KAKAO, String.valueOf(KAKAO_ID))
                    .orElseThrow();

            assertThat(saved.getBirth()).isNull();
            assertThat(saved.getEmail()).isEqualTo("kakao@test.com");
            assertThat(saved.getName()).isEqualTo("강민");
            assertThat(saved.getRole()).isEqualTo(Role.USER);
            assertThat(((CustomOAuth2User) principal).getId()).isEqualTo(saved.getId());
        }

        @Test
        @DisplayName("INSERT 시점에 제약 위반이 발생하지 않는다")
        void doesNotViolateAnyConstraintOnInsert() {
            // birth 컬럼이 nullable이 아니게 되면 여기서 DataIntegrityViolationException으로 잡힌다.
            givenUserInfoResponse(kakaoUserInfo("kakao@test.com", "강민"));

            assertThatCode(() -> {
                memberService.loadUser(userRequest(registration("kakao", "id")));
                em.flush();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("같은 계정으로 다시 로그인하면 회원을 새로 만들지 않는다")
        void reusesExistingMemberOnSecondLogin() {
            givenUserInfoResponse(kakaoUserInfo("kakao@test.com", "강민"));
            ClientRegistration kakao = registration("kakao", "id");

            OAuth2User first = memberService.loadUser(userRequest(kakao));
            em.flush();
            em.clear();
            OAuth2User second = memberService.loadUser(userRequest(kakao));
            em.flush();
            em.clear();

            List<Member> members = memberRepository.findAll();
            assertThat(members).hasSize(1);
            assertThat(((CustomOAuth2User) second).getId()).isEqualTo(((CustomOAuth2User) first).getId());
        }
    }

    @Nested
    @DisplayName("KakaoResponse 생일 필드 계약")
    class KakaoResponseContract {

        @Test
        @DisplayName("생일/프로필은 문자열 \"null\"이 아니라 실제 null을 반환한다")
        void returnsRealNullNotTheStringNull() {
            // "null"을 반환하면 parseBirth의 null/isBlank 가드를 통과해
            // LocalDate.parse("null-null")에서 터진다. 눈에 잘 안 띄는 회귀라 못으로 박아둔다.
            KakaoResponse response = new KakaoResponse(String.valueOf(KAKAO_ID), "kakao@test.com", "강민");

            assertThat(response.getBirthyear()).isNull();
            assertThat(response.getBirthday()).isNull();
            assertThat(response.getProfileUrl()).isNull();
            assertThat(response.getProvider()).isEqualTo(SocialType.KAKAO);
        }
    }

    @Nested
    @DisplayName("네이버 로그인 (생일 정보 있음)")
    class NaverLogin {

        @Test
        @DisplayName("birthyear/birthday가 있으면 LocalDate로 합쳐 저장된다")
        void savesParsedBirth() {
            // 카카오 쪽 null 가드를 넣으면서 정상 파싱 경로가 같이 죽지는 않았는지 확인한다.
            givenUserInfoResponse(Map.of("response", Map.of(
                    "id", "naver-1",
                    "email", "naver@test.com",
                    "name", "강민",
                    "profile_image", "https://example.com/profile.png",
                    "birthyear", "1999",
                    "birthday", "03-05")));

            memberService.loadUser(userRequest(registration("naver", "response")));
            em.flush();
            em.clear();

            Member saved = memberRepository.findBySocialTypeAndProviderIdAndDeletedAtIsNull(
                            SocialType.NAVER, "naver-1")
                    .orElseThrow();

            assertThat(saved.getBirth()).isEqualTo(LocalDate.of(1999, 3, 5));
        }
    }
}