package juby.invest.domain.backtest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class BacktestReqDto {

    public record ReqInfo(

            @NotBlank(message = "종목 코드는 필수입니다.")
            String stockCode,

            @NotNull(message = "1 ~ 5에 맞는 성향을 입력해주세요. (1:안정형, 2:안정추구형, 3: 위험중립형, 4: 적극투자형, 5: 공격투자형")
            int investType,

//            @NotBlank(message = "시작일은 필수입니다.")
            LocalDate startDate,

//            @NotBlank(message = "종료일은 필수입니다.")
            LocalDate endDate
    ){}
}
