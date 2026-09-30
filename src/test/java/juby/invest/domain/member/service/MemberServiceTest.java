package juby.invest.domain.member.service;

import juby.invest.domain.auth.service.AuthService;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.enums.Role;
import juby.invest.domain.member.enums.SocialType;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.domain.member.repository.PersonalityRepository;
import juby.invest.global.security.entity.CustomOAuth2User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberService (회원 탈퇴 soft delete)")
class MemberServiceTest {

    private static final long MEMBER_ID = 1L;

    @Mock private MemberRepository memberRepository;
    @Mock private PersonalityRepository personalityRepository;
    @Mock private AuthService authService;

    @InjectMocks private MemberService memberService;

    private Member member;
    private CustomOAuth2User principal;

    @BeforeEach
    void setUp() {
        member = Member.builder()
                .id(MEMBER_ID)
                .email("kangmin@juby.com")
                .name("강민")
                .socialType(SocialType.NAVER)
                .providerId("naver-1")
                .role(Role.USER)
                .build();

        principal = new CustomOAuth2User(MEMBER_ID, Role.USER, "강민", "jti-1234", LocalDateTime.now().plusMinutes(30));
    }

    private MemberErrorCode errorCodeOf(Throwable e) {
        return (MemberErrorCode) ((MemberException) e).getErrorCode();
    }

    @Nested
    @DisplayName("탈퇴")
    class Withdraw {

        @Test
        @DisplayName("행을 지우지 않고 탈퇴 시각만 기록한다")
        void softDeletes() {
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            memberService.deleteMember(principal);

            assertThat(member.isDeleted()).isTrue();
            assertThat(member.getDeletedAt()).isNotNull();
            // 주문/좋아요 같은 연관 데이터가 FK로 매달려 있어 행을 물리적으로 지울 수 없다.
            then(memberRepository).should(org.mockito.Mockito.never()).delete(member);
        }

        @Test
        @DisplayName("AT 블랙리스트 등록과 RT 삭제는 로그아웃에 위임한다")
        void delegatesTokenInvalidationToLogout() {
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            memberService.deleteMember(principal);

            // 탈퇴는 "로그아웃 + 탈퇴 처리"다. 같은 블록을 두 서비스에 복사하면
            // 한쪽만 고쳐졌을 때 탈퇴한 회원의 AT가 30분간 살아남는다.
            then(authService).should().logout(principal);
        }

        @Test
        @DisplayName("이미 탈퇴한 회원이면 409로 막는다")
        void rejectsAlreadyWithdrawn() {
            member.withdraw();
            LocalDateTime firstWithdrawal = member.getDeletedAt();
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            assertThatThrownBy(() -> memberService.deleteMember(principal))
                    .isInstanceOf(MemberException.class)
                    .extracting(MemberServiceTest.this::errorCodeOf)
                    .isEqualTo(MemberErrorCode.ALREADY_DELETED_MEMBER);

            // soft delete는 행이 남으므로 findById만으로는 걸러지지 않는다. 별도 확인이 필요한 이유다.
            assertThat(member.getDeletedAt()).isEqualTo(firstWithdrawal);
            then(authService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("존재하지 않는 회원이면 404다")
        void rejectsUnknownMember() {
            // 인증 객체는 AT 발급 시점의 정보다. 지금도 회원 행이 있다는 보장이 아니다.
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.deleteMember(principal))
                    .isInstanceOf(MemberException.class)
                    .extracting(MemberServiceTest.this::errorCodeOf)
                    .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);

            then(authService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("탈퇴 경로는 findActiveById가 아니라 findById로 조회한다")
        void looksUpIncludingWithdrawnRows() {
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            memberService.deleteMember(principal);

            // findActiveById로 조회하면 이미 탈퇴한 회원이 404로 나가 409(중복 탈퇴)와 구분되지 않는다.
            then(memberRepository).should().findById(MEMBER_ID);
            then(memberRepository).should(org.mockito.Mockito.never()).findActiveById(MEMBER_ID);
        }
    }

    @Nested
    @DisplayName("탈퇴 후 조회 차단")
    class BlockedAfterWithdrawal {

        @Test
        @DisplayName("내 정보 조회는 탈퇴 회원을 404로 막는다")
        void memberInfoBlocked() {
            // findActiveById가 deleted_at is null 조건을 달고 있어 탈퇴 행이 걸러진다.
            given(memberRepository.findActiveById(MEMBER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.getMemberInfo(MEMBER_ID))
                    .isInstanceOf(MemberException.class)
                    .extracting(MemberServiceTest.this::errorCodeOf)
                    .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("투자유형 조회는 탈퇴 회원을 404로 막는다")
        void personalityInfoBlocked() {
            given(memberRepository.findActiveById(MEMBER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.getPersonalityInfo(MEMBER_ID))
                    .isInstanceOf(MemberException.class)
                    .extracting(MemberServiceTest.this::errorCodeOf)
                    .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("내 정보 수정은 탈퇴 회원을 404로 막는다")
        void infoChangeBlocked() {
            given(memberRepository.findActiveById(MEMBER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.changeMemberInfo(principal, null))
                    .isInstanceOf(MemberException.class)
                    .extracting(MemberServiceTest.this::errorCodeOf)
                    .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("투자유형 변경은 탈퇴 회원을 404로 막는다")
        void personalityChangeBlocked() {
            given(memberRepository.findActiveById(MEMBER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.changePersonality(principal, null))
                    .isInstanceOf(MemberException.class)
                    .extracting(MemberServiceTest.this::errorCodeOf)
                    .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);

            // 회원이 없으면 성향 테이블은 건드리지도 않는다.
            then(personalityRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("살아있는 회원은 정상적으로 조회된다")
        void activeMemberPasses() {
            given(memberRepository.findActiveById(MEMBER_ID)).willReturn(Optional.of(member));

            assertThat(memberService.getMemberInfo(MEMBER_ID).getName()).isEqualTo("강민");
        }
    }
}
