package juby.invest.service;

import juby.invest.dto.TokenDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RestClient investRestClient;

    @Value("${kis.app-key}") private String appKey;
    @Value("${kis.app-secret}") private String appSecret;

    public TokenDto.TokenResponse getAccessToken(){
        TokenDto.TokenRequest request = new TokenDto.TokenRequest("client_credentials", appKey, appSecret);

        TokenDto.TokenResponse response = investRestClient.post()
                .uri("/oauth2/tokenP")
                .body(request) // body에 어떻게 담기는지?
                .retrieve()
                .body(TokenDto.TokenResponse.class);

        if (response == null || response.accessToken() == null){
            throw new RuntimeException("토큰 발급에 실패했습니다.");
        }

        return response;
    }
}
