package juby.invest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenDto {
    public record TokenRequest(
            String grant_type,
            String appkey,
            String appsecret
    ){}

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") int expiresIn
    ){}
}
