package juby.invest.domain.kis.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/***
 * 국내업종 현재지수 조회 응답 (FHPUP02100000).
 * <p>
 * 응답 필드는 40개가 넘지만 화면에 필요한 것만 받는다. 나머지는 Jackson이 무시한다.
 */
@Builder
public record IndexPriceRes(
        @JsonProperty("rt_cd") String rtCd,   // 성공 실패 여부
        @JsonProperty("msg1") String message, // 응답메세지
        @JsonProperty("output") Info output){ // 응답 상세

    @Builder
    public record Info(
            @JsonProperty("bstp_nmix_prpr") String currentIndex,       // 업종 지수 현재가
            @JsonProperty("bstp_nmix_prdy_ctrt") String changeRate,    // 업종 지수 전일 대비율
            @JsonProperty("acml_vol") String accVolume,                // 누적 거래량
            @JsonProperty("acml_tr_pbmn") String accTradingValue,      // 누적 거래 대금
            @JsonProperty("bstp_nmix_oprc") String openIndex,          // 업종 지수 시가
            @JsonProperty("bstp_nmix_hgpr") String highIndex,          // 업종 지수 최고가
            @JsonProperty("bstp_nmix_lwpr") String lowIndex            // 업종 지수 최저가
    ){}
}
