package juby.invest.domain.member.service;

import juby.invest.domain.auth.service.AuthService;
import juby.invest.domain.member.dto.ChangeInvestType;
import juby.invest.domain.member.dto.ChangeMemberInfo;
import juby.invest.domain.member.dto.MemberResDto;
import juby.invest.domain.member.dto.NicknameDto;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.entity.Personality;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.PersonalityException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.exception.code.personality.PersonalityErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.domain.member.repository.PersonalityRepository;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PersonalityRepository personalityRepository;
    private final AuthService authService;

    public MemberResDto.MemberInfo getMemberInfo(Long memberId) {
        Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        return MemberResDto.MemberInfo.builder()
                .name(member.getName())
                .nickname(member.getNickname())
                .email(member.getEmail())
                .birth(member.getBirth())
                .socialType(member.getSocialType())
                .isOnboarded(member.isOnboarded())
                .build();
    }

    public MemberResDto.PersonalityInfo getPersonalityInfo(Long memberId) {
        Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        Personality personality = member.getPersonality();
        if (personality == null) {
            throw new MemberException(MemberErrorCode.PERSONALITY_NOT_FOUND);
        }

        return MemberResDto.PersonalityInfo.builder()
                .investPersonality(personality.getInvestPersonality())
                .description(personality.getDescription())
                .personalityImg(personality.getPersonalityImg())
                .build();
    }

    /***
     * 함수 기능: 회원을 탈퇴 처리(soft delete)하고, 토큰 무효화는 로그아웃 로직에 위임한다.
     * @param user 회원 인증 객체
     */
    @Transactional
    public void deleteMember(CustomOAuth2User user) {

        // 인증 객체는 AT 발급 시점의 정보다. 지금도 회원이 살아있다는 보장이 아니다.
        Member member = memberRepository.findById(user.getId())
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        // soft delete는 행이 남으므로, 이미 탈퇴한 회원인지 별도로 확인해야 한다.
        if (member.isDeleted()) {
            throw new MemberException(MemberErrorCode.ALREADY_DELETED_MEMBER);
        }

        member.withdraw();

        // 탈퇴는 "로그아웃 + 회원 탈퇴 처리"이므로 AT 블랙리스트 등록과 RT 삭제는 로그아웃에 맡긴다.
        authService.logout(user);
    }

    @Transactional
    public ChangeInvestType.PersonalityRes changePersonality(CustomOAuth2User principal, ChangeInvestType.PersonalityReq dto) {

        Long memberId = principal.getId();
        Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        // 성향 변경
        Personality personality = personalityRepository.findById(dto.personalityId())
                .orElseThrow(() -> new PersonalityException(PersonalityErrorCode.PERSONALITY_NOT_FOUND));

        member.updatePersonality(personality);

        return ChangeInvestType.PersonalityRes.builder()
                .modifiedAt(LocalDateTime.now())
                .build();
    }

    @Transactional
    public ChangeMemberInfo.ChangeInfoRes changeMemberInfo(CustomOAuth2User principal, ChangeMemberInfo.ChangeInfoReq dto) {

        Long memberId = principal.getId();
        Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        member.updateInfo(dto);

        return ChangeMemberInfo.ChangeInfoRes.builder()
                .modifiedDate(LocalDateTime.now())
                .build();
    }

    /***
     * 함수 기능: 닉네임을 쓸 수 있는지 확인한다. 본인이 이미 쓰는 닉네임은 사용 가능으로 본다.
     * @param principal 회원 인증 객체
     * @param nickname 확인할 닉네임
     * @return 사용 가능 여부
     */
    public NicknameDto.CheckRes checkNickname(CustomOAuth2User principal, String nickname) {

        boolean available = !memberRepository.existsByNicknameAndIdNot(nickname, principal.getId());

        return NicknameDto.CheckRes.builder()
                .nickname(nickname)
                .available(available)
                .build();
    }

    /***
     * 함수 기능: 닉네임을 설정(변경)한다.
     * @param principal 회원 인증 객체
     * @param dto 설정할 닉네임
     * @return 설정된 닉네임
     */
    @Transactional
    public NicknameDto.NicknameRes changeNickname(CustomOAuth2User principal, NicknameDto.NicknameReq dto) {

        Member member = memberRepository.findActiveById(principal.getId())
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        if (memberRepository.existsByNicknameAndIdNot(dto.nickname(), member.getId())) {
            throw new MemberException(MemberErrorCode.NICKNAME_DUPLICATED);
        }

        member.updateNickname(dto.nickname());

        // 중복 확인과 저장 사이에 다른 회원이 같은 닉네임을 먼저 저장하면 unique 제약에 걸린다.
        // 커밋 시점까지 미루면 500으로 빠지므로, 여기서 flush해 중복 에러로 바꾼다.
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new MemberException(MemberErrorCode.NICKNAME_DUPLICATED);
        }

        return NicknameDto.NicknameRes.builder()
                .nickname(member.getNickname())
                .modifiedAt(LocalDateTime.now())
                .build();
    }
}
