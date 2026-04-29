package juby.invest.global.kisapi.token.service;

import juby.invest.global.kisapi.token.dto.TokenDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RestClient investRestClient;
    private final RestClient realInvestRestClient;

    @Value("${kis.mock.app-key}") private String mockAppKey;
    @Value("${kis.mock.app-secret}") private String mockAppSecret;
    @Value("${kis.real.app-key}") private String realAppKey;
    @Value("${kis.real.app-secret}") private String realAppSecret;

    // accessToken 캐싱을 위함.
    private String mockCachedToken;
    private String realCachedToken;
    private LocalDateTime mockTokenExpirationTime;
    private LocalDateTime realTokenExpirationTime;

    public TokenDto.TokenResponse getMockAccessToken(){

        // 캐시된 accessToken이 존재하고 accessToken 만료 시간 전일 경우
        if (mockCachedToken != null && mockTokenExpirationTime != null && LocalDateTime.now().isBefore(mockTokenExpirationTime)){
            log.info("캐시된 mock토큰으로 조회");
            int seconds = (int)Duration.between(LocalDateTime.now(), mockTokenExpirationTime).getSeconds();
            return new TokenDto.TokenResponse(mockCachedToken, seconds);
        }

        // 캐시된 accessToken이 존재하지 않을 경우
        log.info("새로운 mock토큰을 발급받음");
        TokenDto.TokenRequest request = new TokenDto.TokenRequest("client_credentials", mockAppKey, mockAppSecret);

        TokenDto.TokenResponse response = investRestClient.post()
                .uri("/oauth2/tokenP")
                .body(request) // body에 어떻게 담기는지?
                .retrieve()
                .body(TokenDto.TokenResponse.class);

        if (response == null || response.accessToken() == null){
            log.info("토큰 발급에 실패했습니다.");
            throw new RuntimeException("토큰 발급에 실패했습니다.");
        }

        this.mockCachedToken = response.accessToken();
        this.mockTokenExpirationTime = LocalDateTime.now().plusSeconds(response.expiresIn() - 600);
        return response;
    }

    public TokenDto.TokenResponse getRealAccessToken(){

        // 토큰이 있고, 만료되지 않았을 때
        if (realCachedToken != null && realTokenExpirationTime != null&& LocalDateTime.now().isBefore(realTokenExpirationTime)){
            log.info("캐시된 real토큰으로 조회");
            int seconds = (int)Duration.between(LocalDateTime.now(), realTokenExpirationTime).getSeconds();
            return new TokenDto.TokenResponse(realCachedToken, seconds);
        }

        // 토큰이 없거나 만료되었을 때
        log.info("새로운 real토큰을 발급받음");
        TokenDto.TokenRequest request = new TokenDto.TokenRequest("client_credentials", realAppKey, realAppSecret);
        TokenDto.TokenResponse response = realInvestRestClient.post()
                .uri("/oauth2/tokenP")
                .body(request)
                .retrieve()
                .body(TokenDto.TokenResponse.class);

        if (response == null || response.accessToken() == null){
            log.info("실전 모의 투자 토큰 발급 실패");
            throw new RuntimeException("실전 모의 투자 토큰 발급 실패");
        }

        this.realCachedToken = response.accessToken();
        this.realTokenExpirationTime = LocalDateTime.now().plusSeconds(response.expiresIn() - 600);

        return response;
    }
}
