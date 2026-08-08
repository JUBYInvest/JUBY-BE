package juby.invest.domain.kis.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

public class ApprovalKeyDto {

    @Builder
    public record ApprovalKeyReq(

            @JsonProperty("grant_type")
            String grantType,

            @JsonProperty("appkey")
            String appKey,

            @JsonProperty("secretkey")
            String secretKey
    ){
        public static ApprovalKeyReq of(String appKey, String secretKey){
            return ApprovalKeyReq.builder()
                    .grantType("client_credentials")
                    .appKey(appKey)
                    .secretKey(secretKey)
                    .build();
        }
    }

    public record ApprovalKeyRes(

            @JsonProperty("approval_key")
            String approvalKey
    ){}
}
