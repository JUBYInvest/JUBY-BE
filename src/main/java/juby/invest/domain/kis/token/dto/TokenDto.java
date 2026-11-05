package juby.invest.domain.kis.token.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

public class TokenDto {

    @Builder
    public record TokenRequest(
            @NotBlank(message = "권한부여 Type은 필수입니다.")
            @JsonProperty("grant_type")
            String grantType,

            @NotBlank(message = "앱키는 필수입니다.")
            @JsonProperty("appkey")
            String appKey,

            @NotBlank(message = "앱시크릿키는 필수입니다.")
            @JsonProperty("appsecret")
            String appSecret
    ){}

    /***
     * 웹소켓 접속키(approval_key) 발급 요청.
     * access_token 발급(TokenRequest)과 달리 시크릿 키의 필드명이 appsecret이 아니라 secretkey다.
     * 그래서 TokenRequest를 재사용할 수 없다.
     */
    @Builder
    public record ApprovalRequest(
            @NotBlank(message = "권한부여 Type은 필수입니다.")
            @JsonProperty("grant_type")
            String grantType,

            @NotBlank(message = "앱키는 필수입니다.")
            @JsonProperty("appkey")
            String appKey,

            @NotBlank(message = "앱시크릿키는 필수입니다.")
            @JsonProperty("secretkey")
            String secretKey
    ){}

    public record ApprovalResponse(
            @JsonProperty("approval_key")
            String approvalKey
    ){}

    @Builder
    public record TokenResponse(
            @JsonProperty("access_token")
            String accessToken,

            @JsonProperty("expires_in")
            int expiresIn,

            @JsonProperty("access_token_token_expired")
            String expiresAt
    ){}
}
