package juby.invest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TradingVolumeDto(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg1") String message,
        @JsonProperty("output") List<Output> outputList
){
    public record Output(
            @JsonProperty("hts_kor_isnm") String stockName,
            @JsonProperty("data_rank") String dataRank,
            @JsonProperty("stck_prpr") String currentPrice,
            @JsonProperty("avrg_tr_pbmn") String averageVolume
    ){}
}
