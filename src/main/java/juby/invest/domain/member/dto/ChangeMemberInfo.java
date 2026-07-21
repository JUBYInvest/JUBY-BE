package juby.invest.domain.member.dto;

import jakarta.validation.constraints.PastOrPresent;
import lombok.Builder;

import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ChangeMemberInfo {

    public record ChangeInfoReq(

            @Size(min = 2, max = 4, message = "이름은 2~4자여야 합니다.")
            String name,

            @PastOrPresent(message = "생일은 오늘 이전이어야 합니다.")
            LocalDate birth
    ){}

    @Builder
    public record ChangeInfoRes(
            LocalDateTime modifiedDate
    ){}
}
