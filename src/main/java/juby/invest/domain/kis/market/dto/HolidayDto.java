package juby.invest.domain.kis.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record HolidayDto(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg1") String message,
        @JsonProperty("output") List<Output> output
){
    public record Output(
            @JsonProperty("bass_dt") String baseDate, // 기준일자 (YYYYMMDD)
            @JsonProperty("bzdy_yn") String businessDay, // 영업일여부 (금융기관이 업무를 하는 날)
            @JsonProperty("tr_day_yn") String tradeDay, // 거래일 여부 (증권 업무가 가능한 날)
            @JsonProperty("opnd_yn") String openDay // 개장일 여부 (주식시장이 개방되는 날)
    ){}
}
