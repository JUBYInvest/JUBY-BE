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
