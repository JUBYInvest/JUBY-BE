package juby.invest.domain.kis.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PeriodStockPriceDto(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg1") String message,
        @JsonProperty("output2") List<Output> output
){
    public record Output(
        @JsonProperty("stck_bsop_date") String date,
        @JsonProperty("stck_clpr") String closePrice,
        @JsonProperty("stck_oprc") String openPrice,
        @JsonProperty("stck_hgpr") String highPrice,
        @JsonProperty("stck_lwpr") String lowPrice,
        @JsonProperty("acml_vol") String volume){}
}
