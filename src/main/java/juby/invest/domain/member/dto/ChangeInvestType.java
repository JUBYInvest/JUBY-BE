package juby.invest.domain.member.dto;

import lombok.Builder;

import java.time.LocalDateTime;

public class ChangeInvestType {

    public record PersonalityReq(
            Long personalityId
    ){}

    @Builder
    public record PersonalityRes(
            LocalDateTime modifiedAt
    ){}
}
