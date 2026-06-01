package juby.invest.domain.backtest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Builder
@Getter
public class BacktestReqDto {

    @Builder
    public record ReqInfo(

            @NotBlank(message = "종목 코드는 필수입니다.")
            String stockCode,

            @NotBlank(message = "알맞은 전략을 찾지 못하였습니다.")
            String strategyName,

            @NotBlank(message = "시작일은 필수입니다.")
            LocalDate startDate,

            @NotBlank(message = "종료일은 필수입니다.")
            LocalDate endDate
    ){}
}
