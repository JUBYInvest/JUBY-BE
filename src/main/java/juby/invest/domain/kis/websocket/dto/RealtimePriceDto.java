package juby.invest.domain.kis.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

public class RealtimePriceDto {

    public record RealtimePriceReq(

            @JsonProperty("tr_id")
            String trId,

            @JsonProperty("tr_key")
            String trKey
    ){}

    @Builder
    public record RealtimePriceRes(
            // 번호, 종목명, 현재가, 등락률, 거래대금
            @JsonProperty("MKSC_SHRN_ISCD")
            String stockCode,

            @JsonProperty("STCK_PRPR")
            String currentPrice,

            @JsonProperty("PRDY_CTRT")
            String changeRate,

            @JsonProperty("ACML_TR_PMMN")
            String accumulatedAmount
    ){
        public static RealtimePriceRes from(String[] f){
            return RealtimePriceRes.builder()
                    .stockCode(f[0])
                    .currentPrice(f[2])
                    .changeRate(f[5])
                    .accumulatedAmount(f[14])
                    .build();
        }
    }
}
