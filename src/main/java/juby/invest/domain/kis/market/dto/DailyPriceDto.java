package juby.invest.domain.kis.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record DailyPriceDto(
        @JsonProperty("rt_cd") String rtCd, // 성공 실패 여부
        @JsonProperty("msg1") String message, // 응답메세지
        @JsonProperty("output") DailyPriceRes output){ // 응답 상세
    @Builder
    public record DailyPriceRes(
            @JsonProperty("stck_prpr") String currentPrice,
            @JsonProperty("stck_oprc") String openPrice,
            @JsonProperty("stck_hgpr") String highPrice,
            @JsonProperty("stck_lwpr") String lowPrice,
            @JsonProperty("acml_vol") String volume,
            @JsonProperty("prdy_vrss") String compareYesterday,
            @JsonProperty("prdy_vrss_sign") String compareYesterdaySign,
            @JsonProperty("prdy_ctrt") String dayChange
    ){}
}

