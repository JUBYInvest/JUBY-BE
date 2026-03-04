package juby.invest.service;

import juby.invest.dto.TokenDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RestClient investRestClient;

    @Value("${kis.app-key}") private String appKey;
    @Value("${kis.app-secret}") private String appSecret;

    // accessToken 캐싱을 위함.
    private String cachedToken;
    private LocalDateTime tokenExpirationTime;

    public TokenDto.TokenResponse getAccessToken(){

        // 캐시된 accessToken이 존재하고 accessToken 만료 시간 전일 경우
        if (cachedToken != null && tokenExpirationTime != null && LocalDateTime.now().isBefore(tokenExpirationTime)){
            log.info("캐시된 토큰으로 조회");
            int seconds = (int)Duration.between(LocalDateTime.now(), tokenExpirationTime).getSeconds();
            return new TokenDto.TokenResponse(cachedToken, seconds);
        }

        // 캐시된 accessToken이 존재하지 않을 경우
        log.info("새로운 토큰을 발급받음");
        TokenDto.TokenRequest request = new TokenDto.TokenRequest("client_credentials", appKey, appSecret);

        TokenDto.TokenResponse response = investRestClient.post()
                .uri("/oauth2/tokenP")
                .body(request) // body에 어떻게 담기는지?
                .retrieve()
                .body(TokenDto.TokenResponse.class);

        if (response == null || response.accessToken() == null){
            log.info("토큰 발급에 실패했습니다.");
            throw new RuntimeException("토큰 발급에 실패했습니다.");
        }

        this.cachedToken = response.accessToken();
        this.tokenExpirationTime = LocalDateTime.now().plusSeconds(response.expiresIn() - 600);
        return response;
    }
}
