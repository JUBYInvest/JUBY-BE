package juby.invest.domain.auth.dto;

import lombok.Builder;

public class LoginResDto{

    @Builder
    public record LoginRes(
            String accessToken,
            String refreshToken
    ){}
}
