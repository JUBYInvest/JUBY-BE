package juby.invest.global.api.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DailyStockPriceDto(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg1") String message,
        @JsonProperty("output") List<Output> output
){
    public record Output(
        @JsonProperty("stck_bsop_date") String date,
        @JsonProperty("stck_oprc") String openPrice,
        @JsonProperty("stck_hgpr") String highPrice,
        @JsonProperty("stck_lwpr") String lowPrice,
        @JsonProperty("stck_clpr") String closePrice,
        @JsonProperty("acml_vol") String volume
    ){}
}