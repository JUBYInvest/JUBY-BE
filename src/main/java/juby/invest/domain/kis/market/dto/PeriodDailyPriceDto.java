package juby.invest.domain.kis.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

public record PeriodDailyPriceDto(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg1") String message,
        @JsonProperty("output2") List<Output> output
){
    @Builder
    public record Output(
        @JsonProperty("stck_bsop_date") String date,
        @JsonProperty("stck_clpr") String closePrice,
        @JsonProperty("stck_oprc") String openPrice,
        @JsonProperty("stck_hgpr") String highPrice,
        @JsonProperty("stck_lwpr") String lowPrice,
        @JsonProperty("acml_vol") String volume,
        @JsonProperty("acml_tr_pbmn") String tradingValue // 누적 거래대금
    ){}
}
