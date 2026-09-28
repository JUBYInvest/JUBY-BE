package juby.invest.domain.auth.service;

import io.jsonwebtoken.Claims;
import juby.invest.domain.auth.dto.ReissueDto;
import juby.invest.domain.auth.entity.AccessTokenBlacklist;
import juby.invest.domain.auth.entity.RefreshToken;
import juby.invest.domain.auth.exception.AuthException;
import juby.invest.domain.auth.exception.code.AuthErrorCode;
import juby.invest.domain.auth.repository.AccessTokenBlacklistRepository;
import juby.invest.domain.auth.repository.RefreshTokenRepository;
import juby.invest.domain.auth.util.TokenHasher;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.enums.Role;
import juby.invest.domain.member.enums.SocialType;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.global.security.entity.CustomOAuth2User;
import juby.invest.global.security.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService (AT 재발급 / 로그아웃)")
class AuthServiceTest {

    private static final String SECRET = "juby-invest-test-secret-key-must-be-at-least-32-bytes-long";
    private static final String OTHER_SECRET = "attacker-forged-secret-key-also-at-least-32-bytes-long!!!";
    private static final long AT_VALIDITY = 1_800_000L;    // 30분
    private static final long RT_VALIDITY = 1_209_600_000L; // 14일
    private static final long MEMBER_ID = 1L;

    @Mock private MemberRepository memberRepository;
    @Mock private AccessTokenBlacklistRepository blacklistRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    // JWT는 모킹하지 않고 실제로 서명/검증한다. 서명 위조나 만료 판정을 진짜로 확인해야 의미가 있다.
    private JwtUtil jwtUtil;
    private AuthService authService;

    private Member member;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, AT_VALIDITY, RT_VALIDITY);
        authService = new AuthService(memberRepository, blacklistRepository, refreshTokenRepository, jwtUtil);
        ReflectionTestUtils.setField(authService, "rtValidity", RT_VALIDITY);

        member = Member.builder()
                .id(MEMBER_ID)
                .email("kangmin@juby.com")
                .name("강민")
                .socialType(SocialType.NAVER)
                .providerId("naver-1")
                .role(Role.USER)
                .build();
    }

    /** 저장된 RT row를 흉내낸다. 저장값은 항상 해시다. */
    private RefreshToken savedRT(String rawToken) {
        return RefreshToken.builder()
                .member(member)
                .token(TokenHasher.hash(rawToken))
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build();
    }

    private void givenActiveMemberWithRT(RefreshToken rt) {
        given(memberRepository.findActiveById(MEMBER_ID)).willReturn(Optional.of(member));
        given(refreshTokenRepository.findByMember(member)).willReturn(Optional.of(rt));
    }

    private AuthErrorCode errorCodeOf(Throwable e) {
        return (AuthErrorCode) ((AuthException) e).getErrorCode();
    }

    @Nested
    @DisplayName("재발급 성공")
    class ReissueSuccess {

        @Test
        @DisplayName("새 AT를 발급한다")
        void issuesNewAccessToken() {
            String rawRT = jwtUtil.createRefreshToken(MEMBER_ID);
            givenActiveMemberWithRT(savedRT(rawRT));

            ReissueDto.ReissueRes result = authService.reissue(rawRT);

            Claims claims = jwtUtil.parseClaims(result.accessToken());
            assertThat(claims.get("typ", String.class)).isEqualTo("access");
            assertThat(claims.getSubject()).isEqualTo(String.valueOf(MEMBER_ID));
            assertThat(claims.get("role", String.class)).isEqualTo("USER");
            assertThat(claims.get("name", String.class)).isEqualTo("강민");
            // jti가 없으면 로그아웃 시 블랙리스트에 등록할 키가 없다.
            assertThat(claims.getId()).isNotBlank();
        }

        @Test
        @DisplayName("RT를 로테이션해 저장한다")
        void rotatesRefreshToken() {
            String rawRT = jwtUtil.createRefreshToken(MEMBER_ID);
            RefreshToken stored = savedRT(rawRT);
            givenActiveMemberWithRT(stored);

            ReissueDto.ReissueRes result = authService.reissue(rawRT);

            // 응답으로 나간 RT와 DB에 남은 해시가 일치해야 다음 재발급이 성공한다.
            // 원문을 그대로 저장하면 length=43 컬럼에서 잘려 두 번째 재발급부터 실패한다.
            assertThat(stored.getToken()).isEqualTo(TokenHasher.hash(result.refreshToken()));
            assertThat(stored.getToken()).hasSize(43);
        }

        @Test
        @DisplayName("로테이션한 RT는 예전 RT와 다른 토큰이다")
        void issuesDistinctRefreshToken() {
            String rawRT = jwtUtil.createRefreshToken(MEMBER_ID);
            givenActiveMemberWithRT(savedRT(rawRT));

            ReissueDto.ReissueRes result = authService.reissue(rawRT);

            // RT에 jti가 없으면 sub/typ/iat/exp만 남는데 iat/exp는 초 단위다.
            // 1초 안에 재발급하면 예전 RT와 완전히 같은 문자열이 나와, 탈취된 예전 RT가 그대로 유효해진다.
            assertThat(result.refreshToken()).isNotEqualTo(rawRT);
        }

        @Test
        @DisplayName("RT 만료 시각을 다시 14일 뒤로 늘린다")
        void extendsExpiry() {
            String rawRT = jwtUtil.createRefreshToken(MEMBER_ID);
            RefreshToken stored = savedRT(rawRT);
            ReflectionTestUtils.setField(stored, "expiresAt", LocalDateTime.now().plusMinutes(1));
            givenActiveMemberWithRT(stored);

            authService.reissue(rawRT);

            assertThat(stored.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(13));
        }
    }

    @Nested
    @DisplayName("재발급 실패")
    class ReissueFailure {

        @DisplayName("RT 쿠키가 없으면 401(RT_COOKIE_MISSING)이다")
        @ParameterizedTest(name = "refreshToken=[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void rejectsMissingCookie(String rawRT) {
            // 쿠키 만료는 정상적인 흐름이다. 400(형식 오류)으로 나가면 프론트의 재로그인 트리거를 비껴간다.
            assertThatThrownBy(() -> authService.reissue(rawRT))
                    .isInstanceOf(AuthException.class)
                    .extracting(AuthServiceTest.this::errorCodeOf)
                    .isEqualTo(AuthErrorCode.RT_COOKIE_MISSING);

            then(memberRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("만료된 RT는 401(EXPIRED_TOKEN)이다")
        void rejectsExpiredToken() {
            // 만료 판정에는 60초 clock skew가 있으므로 그보다 더 과거로 만든다.
            String expiredRT = new JwtUtil(SECRET, AT_VALIDITY, -120_000L).createRefreshToken(MEMBER_ID);

            assertThatThrownBy(() -> authService.reissue(expiredRT))
                    .isInstanceOf(AuthException.class)
                    .extracting(AuthServiceTest.this::errorCodeOf)
                    .isEqualTo(AuthErrorCode.EXPIRED_TOKEN);
        }

        @Test
        @DisplayName("다른 키로 서명된 RT는 401(INVALID_TOKEN)이다")
        void rejectsForgedSignature() {
            String forgedRT = new JwtUtil(OTHER_SECRET, AT_VALIDITY, RT_VALIDITY).createRefreshToken(MEMBER_ID);

            assertThatThrownBy(() -> authService.reissue(forgedRT))
                    .isInstanceOf(AuthException.class)
                    .extracting(AuthServiceTest.this::errorCodeOf)
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("JWT 형식이 아니면 401(INVALID_TOKEN)이다")
        void rejectsMalformedToken() {
            assertThatThrownBy(() -> authService.reissue("not-a-jwt"))
                    .isInstanceOf(AuthException.class)
                    .extracting(AuthServiceTest.this::errorCodeOf)
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("AT를 RT 자리에 넣으면 401(INVALID_TOKEN)이다")
        void rejectsAccessTokenAsRefreshToken() {
            // 서명도 만료도 멀쩡하므로 typ 클레임을 확인하지 않으면 그대로 통과한다.
            // 30분짜리 AT가 14일짜리 RT로 승격되는 셈이다.
            String accessToken = jwtUtil.createAccessToken(MEMBER_ID, "USER", "강민");

            assertThatThrownBy(() -> authService.reissue(accessToken))
                    .isInstanceOf(AuthException.class)
                    .extracting(AuthServiceTest.this::errorCodeOf)
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);

            then(memberRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("저장된 해시와 다르면 401(INVALID_TOKEN)이다")
        void rejectsRotatedOutToken() {
            // 이미 로테이션되어 DB에는 새 RT의 해시가 들어있는 상황.
            // 공격자가 탈취한 예전 RT를 재사용하면 여기서 걸린다.
            String oldRT = jwtUtil.createRefreshToken(MEMBER_ID);
            givenActiveMemberWithRT(savedRT("완전히-다른-토큰"));

            assertThatThrownBy(() -> authService.reissue(oldRT))
                    .isInstanceOf(AuthException.class)
                    .extracting(AuthServiceTest.this::errorCodeOf)
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("저장된 RT가 없으면 401(RT_NOT_FOUND)이다")
        void rejectsWhenNoStoredToken() {
            // 로그아웃으로 RT row가 지워진 뒤 남아있던 쿠키로 재발급을 시도하는 경우.
            String rawRT = jwtUtil.createRefreshToken(MEMBER_ID);
            given(memberRepository.findActiveById(MEMBER_ID)).willReturn(Optional.of(member));
            given(refreshTokenRepository.findByMember(member)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.reissue(rawRT))
                    .isInstanceOf(AuthException.class)
                    .extracting(AuthServiceTest.this::errorCodeOf)
                    .isEqualTo(AuthErrorCode.RT_NOT_FOUND);
        }

        @Test
        @DisplayName("탈퇴한 회원에게는 새 AT를 발급하지 않는다")
        void rejectsWithdrawnMember() {
            // findActiveById는 deleted_at이 채워진 행을 걸러낸다.
            // soft delete라 행 자체는 남아 있으므로, findById를 썼다면 통과해버렸을 경로다.
            String rawRT = jwtUtil.createRefreshToken(MEMBER_ID);
            given(memberRepository.findActiveById(MEMBER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.reissue(rawRT))
                    .isInstanceOf(MemberException.class)
                    .extracting(e -> ((MemberException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);

            then(refreshTokenRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("실패하면 RT를 로테이션하지 않는다")
        void doesNotRotateOnFailure() {
            String oldRT = jwtUtil.createRefreshToken(MEMBER_ID);
            RefreshToken stored = savedRT("완전히-다른-토큰");
            String beforeHash = stored.getToken();
            givenActiveMemberWithRT(stored);

            assertThatThrownBy(() -> authService.reissue(oldRT)).isInstanceOf(AuthException.class);

            // 검증 실패인데 RT가 갱신되면 정상 세션까지 끊긴다.
            assertThat(stored.getToken()).isEqualTo(beforeHash);
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        private CustomOAuth2User principal(String jti, LocalDateTime expiresAt) {
            return new CustomOAuth2User(MEMBER_ID, Role.USER, "강민", jti, expiresAt);
        }

        @Test
        @DisplayName("현재 AT의 jti를 블랙리스트에 등록한다")
        void blacklistsCurrentAccessToken() {
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

            authService.logout(principal("jti-1234", expiresAt));

            ArgumentCaptor<AccessTokenBlacklist> captor = ArgumentCaptor.forClass(AccessTokenBlacklist.class);
            then(blacklistRepository).should().save(captor.capture());

            assertThat(captor.getValue().getJti()).isEqualTo("jti-1234");
            // 스케줄러가 정리 기준으로 쓰는 값이다. AT가 자체 만료되는 시각과 같아야 한다.
            assertThat(captor.getValue().getExpiresAt()).isEqualTo(expiresAt);
        }

        @Test
        @DisplayName("회원의 RT를 삭제한다")
        void deletesRefreshToken() {
            authService.logout(principal("jti-1234", LocalDateTime.now().plusMinutes(30)));

            // RT가 남으면 재발급 API로 곧바로 새 AT를 받아갈 수 있어 로그아웃이 무의미해진다.
            then(refreshTokenRepository).should().deleteByMember_Id(MEMBER_ID);
        }
    }

    @Nested
    @DisplayName("블랙리스트 조회")
    class Blacklist {

        @Test
        @DisplayName("등록된 jti는 true를 반환한다")
        void returnsTrueForBlacklisted() {
            given(blacklistRepository.existsById("jti-1234")).willReturn(true);

            assertThat(authService.isBlacklisted("jti-1234")).isTrue();
        }

        @Test
        @DisplayName("등록되지 않은 jti는 false를 반환한다")
        void returnsFalseForUnknown() {
            given(blacklistRepository.existsById("jti-9999")).willReturn(false);

            assertThat(authService.isBlacklisted("jti-9999")).isFalse();
        }
    }
}