package juby.invest.global.security.dto;

import lombok.Builder;

public class LoginResDto{

    @Builder
    public record LoginRes(
            String accessToken,
            String refreshToken
    ){}
}
