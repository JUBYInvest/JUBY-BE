package juby.invest.domain.member.dto;

import juby.invest.domain.member.enums.InvestPersonality;
import juby.invest.domain.member.enums.SocialType;
import lombok.Builder;
import lombok.Getter;

public class MemberResDto {

    @Getter
    @Builder
    public static class MemberInfo {
        private String name;
        private String email;
        private String birth;
        private SocialType socialType;
    }

    @Getter
    @Builder
    public static class PersonalityInfo {
        private InvestPersonality investPersonality;
        private String description;
        private String personalityImg;
    }
}
