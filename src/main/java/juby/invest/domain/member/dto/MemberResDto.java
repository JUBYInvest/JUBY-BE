package juby.invest.domain.member.dto;

import juby.invest.domain.member.enums.InvestPersonality;
import juby.invest.domain.member.enums.SocialType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

public class MemberResDto {

    @Getter
    @Builder
    public static class MemberInfo {
        private String name;
        private String nickname; // null이면 닉네임 미설정 → 프론트가 작성 시점에 설정 팝업을 띄운다.
        private String email;
        private LocalDate birth;
        private SocialType socialType;
        private boolean isOnboarded;
    }

    @Getter
    @Builder
    public static class PersonalityInfo {
        private InvestPersonality investPersonality;
        private String description;
        private String personalityImg;
    }
}
