package juby.invest.global.api.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CurrentPriceDto(
        @JsonProperty("rt_cd") String rtCd, // 성공 실패 여부
        @JsonProperty("msg1") String message, // 응답메세지
        @JsonProperty("output") Output output){ // 응답 상세
    public record Output(
            @JsonProperty("stck_prpr") String currentPrice,
            @JsonProperty("prdy_vrss") String compareYesterday
    ){}
}
