package juby.invest.domain.kis.token.service;

import juby.invest.domain.kis.token.dto.TokenDto;
import juby.invest.domain.kis.token.entity.KisToken;
import juby.invest.domain.kis.token.enums.TokenType;
import juby.invest.domain.kis.token.exception.TokenException;
import juby.invest.domain.kis.token.exception.code.TokenErrorCode;
import juby.invest.domain.kis.token.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TokenService {

    private final RestClient mockInvestRestClient;
    private final RestClient realInvestRestClient;
    private final TokenRepository tokenRepository;

    @Value("${kis.mock.app-key}") private String mockAppKey;
    @Value("${kis.mock.app-secret}") private String mockAppSecret;
    @Value("${kis.real.app-key}") private String realAppKey;
    @Value("${kis.real.app-secret}") private String realAppSecret;

    /**
     *  mockAccessToken을 반환
     */
    public TokenDto.TokenResponse getMockAccessToken(){ return getAccessToken(TokenType.MOCK); }

    /***
     * realAccessToken을 반환
     */
    public TokenDto.TokenResponse getRealAccessToken(){
        return getAccessToken(TokenType.REAL);
    }

    /***
     * (mock/real) accessToken을 반환한다.
     */
    private TokenDto.TokenResponse getAccessToken(TokenType tokenType){
        Optional<KisToken> optionalKisToken = tokenRepository.findByTokenType(tokenType);

        TokenDto.TokenResponse response;

        // DB에 토큰이 있고, 유효하다면 기존 토큰 값 반환
        if (optionalKisToken.isPresent() && isValid(optionalKisToken.get())){

            log.info("기존 {} 토큰이 반환됩니다. 토큰 값: {}", tokenType, optionalKisToken.get().getTokenValue());

            response = TokenDto.TokenResponse.builder()
                    .accessToken(optionalKisToken.get().getTokenValue())
                    .expiresAt(optionalKisToken.get().getExpiredAt().toString())
                    .build();
        } else { // DB에 토큰이 없거나, 유효하지 않다면 새로 토큰을 만들어 반환
            response = callKisTokenApi(tokenType);

            // 기존 토큰이 만료된 경우 -> update
            if (optionalKisToken.isPresent()) {
                log.info("기존 {} 토큰이 최신화됩니다. 토큰 값: {}", tokenType, response.accessToken());
                KisToken kisToken = optionalKisToken.get();
                kisToken.updateToken(response.accessToken(), stringToLocalDateTime(response.expiresAt()));
            } else { // 토큰이 아예 없는 경우 -> save
                log.info("새로 {} 토큰이 발급됩니다. 토큰 값: {}", tokenType, response.accessToken());
                KisToken kisToken = KisToken.builder()
                        .tokenType(tokenType)
                        .tokenValue(response.accessToken())
                        .expiredAt(stringToLocalDateTime(response.expiresAt()))
                        .build();

                tokenRepository.save(kisToken);
            }
        }
        return response;
    }

    /***
     * KIS API에 토큰 발급 요청을 보낸다.
     */
    private TokenDto.TokenResponse callKisTokenApi(TokenType tokenType) {
        String appKey = tokenType.name().equals("MOCK") ? mockAppKey : realAppKey;
        String appSecret = tokenType.name().equals("MOCK") ? mockAppSecret : realAppSecret;
        RestClient investRestClient = tokenType.name().equals("MOCK") ? mockInvestRestClient : realInvestRestClient;

        TokenDto.TokenRequest request = TokenDto.TokenRequest.builder()
                .grantType("client_credentials")
                .appKey(appKey)
                .appSecret(appSecret)
                .build();

        return investRestClient.post()
                .uri((uriBuilder -> uriBuilder
                        .path("/oauth2/tokenP")
                        .build()))
                .body(request)
                .header("application/json;charset=UTF-8")
                .retrieve()
                .body(TokenDto.TokenResponse.class);
    }

    /***
     * 토큰의 만료시간이 유효한지 확인한다.
     * @param kisToken 토큰 엔티티
     * @return true/false
     */
    private boolean isValid(KisToken kisToken) {
        // 현재 시간 + 10분이 토큰의 유효시간보다 이전이어야 한다.
        LocalDateTime nowPlus10Min = LocalDateTime.now().plusMinutes(10);

        return nowPlus10Min.isBefore(kisToken.getExpiredAt());
    }

    /***
     * String -> LocalDateTime으로 변환하는 함수
     * @param dateStr KIS 토큰 발급 API 응답값
     * @return 만료시간 (localDateTime)
     */
    private LocalDateTime stringToLocalDateTime(String dateStr) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return LocalDateTime.parse(dateStr, formatter);
    }
}
