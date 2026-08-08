package juby.invest.domain.kis.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

public class RealtimeSubscribeDto {

    @Builder
    public record RealtimeSubscribeReq(
            Header header,
            Body body
    ){
        public static RealtimeSubscribeReq of(String approvalKey, String trType, String trKey){
            return RealtimeSubscribeReq.builder()
                    .header(Header.of(approvalKey, trType))
                    .body(Body.of(trKey))
                    .build();
        }
    }

    @Builder
    public record Header(
            @JsonProperty("approval_key")
            String approvalKey, // 웹소켓 접속키

            @JsonProperty("custtype")
            String custtype, // 개인

            @JsonProperty("tr_type")
            String trType, // 거래타입

            @JsonProperty("content-type")
            String contentTYpe // 컨텐츠 타입
    ){
        public static Header of(String approvalKey, String trType){
           return Header.builder()
                   .approvalKey(approvalKey)
                   .custtype("P")
                   .trType(trType)
                   .contentTYpe("utf-8")
                   .build();
        }
    }

    @Builder
    public record Body(
            @JsonProperty("tr_id")
            String trId, // 거래ID

            @JsonProperty("tr_key")
            String trKey // 종목번호
    ){
        public static Body of(String trKey){
            return Body.builder()
                    .trId("H0STCNT0")
                    .trKey(trKey)
                    .build();
        }
    }
}
