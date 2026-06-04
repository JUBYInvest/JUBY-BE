package juby.invest.domain.kis.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record HolidayDto(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg1") String message,
        @JsonProperty("output") List<Output> output
){
    public record Output(
            @JsonProperty("bass_dt") String baseDate,
            @JsonProperty("bzdy_yn") String openDay,
            @JsonProperty("tr_day_yn") String tradeDay
    ){}
}
