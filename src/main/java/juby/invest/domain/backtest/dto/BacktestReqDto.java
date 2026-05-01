package juby.invest.domain.backtest.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Builder
@Getter
public class BacktestReqDto {

    @Builder
    public record ReqInfo(
            String stockCode,
            String strategyName,
            LocalDate startDate,
            LocalDate endDate
    ){}
}
