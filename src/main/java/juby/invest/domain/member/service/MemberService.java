package juby.invest.domain.member.service;

import juby.invest.domain.member.dto.ChangeInvestType;
import juby.invest.domain.member.dto.ChangeMemberInfo;
import juby.invest.domain.member.dto.MemberResDto;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.entity.Personality;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.PersonalityException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.exception.code.personality.PersonalityErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.domain.member.repository.PersonalityRepository;
import juby.invest.global.apiPayload.exception.ProjectException;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PersonalityRepository personalityRepository;

    public MemberResDto.MemberInfo getMemberInfo(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        return MemberResDto.MemberInfo.builder()
                .name(member.getName())
                .email(member.getEmail())
                .birth(member.getBirth())
                .socialType(member.getSocialType())
                .build();
    }

    public MemberResDto.PersonalityInfo getPersonalityInfo(Long memberId) {
        Member member = memberRepository.findById(memberId)
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

    @Transactional
    public void deleteMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        memberRepository.delete(member);
    }

    @Transactional
    public ChangeInvestType.PersonalityRes changePersonality(CustomOAuth2User principal, ChangeInvestType.PersonalityReq dto) {

        Long memberId = principal.getId();
        Member member = memberRepository.findById(memberId)
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
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        member.updateInfo(dto);

        return ChangeMemberInfo.ChangeInfoRes.builder()
                .modifiedDate(LocalDateTime.now())
                .build();
    }
}
