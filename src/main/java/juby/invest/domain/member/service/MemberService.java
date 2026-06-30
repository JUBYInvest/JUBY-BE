package juby.invest.domain.member.service;

import juby.invest.domain.member.dto.MemberResDto;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.entity.Personality;
import juby.invest.domain.member.exception.code.MemberErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.global.apiPayload.exception.ProjectException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberResDto.MemberInfo getMemberInfo(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ProjectException(MemberErrorCode.MEMBER_NOT_FOUND));

        return MemberResDto.MemberInfo.builder()
                .name(member.getName())
                .email(member.getEmail())
                .birth(member.getBirth())
                .socialType(member.getSocialType())
                .build();
    }

    public MemberResDto.PersonalityInfo getPersonalityInfo(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ProjectException(MemberErrorCode.MEMBER_NOT_FOUND));

        Personality personality = member.getPersonality();
        if (personality == null) {
            throw new ProjectException(MemberErrorCode.PERSONALITY_NOT_FOUND);
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
                .orElseThrow(() -> new ProjectException(MemberErrorCode.MEMBER_NOT_FOUND));

        memberRepository.delete(member);
    }
}
