package juby.invest.domain.backtest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import juby.invest.domain.backtest.enums.BacktestPeriod;
import lombok.Getter;

@Getter
public class BacktestReqDto {

    public record ReqInfo(

            @NotBlank(message = "종목 코드는 필수입니다.")
            String stockCode,

            @NotNull(message = "1 ~ 5에 맞는 성향을 입력해주세요. (1:안정형, 2:안정추구형, 3: 위험중립형, 4: 적극투자형, 5: 공격투자형")
            int investType,

            @NotNull(message = "기간 프리셋은 필수입니다. (ONE_MONTH, THREE_MONTHS, SIX_MONTHS, ONE_YEAR)")
            BacktestPeriod period
    ){}
}
