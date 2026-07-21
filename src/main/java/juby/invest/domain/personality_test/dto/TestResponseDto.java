package juby.invest.domain.personality_test.dto;

import juby.invest.domain.member.enums.InvestPersonality;
import lombok.Builder;

import java.util.List;

public class TestResponseDto{

    public record TestResultReq(
            List<Integer> scores
    ){}

    @Builder
    public record TestResultRes(
            Long memberId,
            String memberName,
            Long personalityId,
            InvestPersonality personalityName,
            String description,
            String url
    ){}
}
