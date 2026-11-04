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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${kis.mock.app-key}") private String mockAppKey;
    @Value("${kis.mock.app-secret}") private String mockAppSecret;
    @Value("${kis.real.app-key}") private String realAppKey;
    @Value("${kis.real.app-secret}") private String realAppSecret;

    /**
     *  mockAccessToken을 반환
     */
    public TokenDto.TokenResponse getMockAccessToken() throws InterruptedException { return getAccessToken(TokenType.MOCK); }

    /***
     * realAccessToken을 반환
     */
    public TokenDto.TokenResponse getRealAccessToken() throws InterruptedException {
        return getAccessToken(TokenType.REAL);
    }

    /***
     * (mock/real) accessToken을 반환한다.
     */
    private TokenDto.TokenResponse getAccessToken(TokenType tokenType) throws InterruptedException {
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

            if (tokenType.name().equals("MOCK")){
                Thread.sleep(1000); // 초당 1건 호출 제한
            }

            // 기존 토큰이 만료된 경우 -> update
            if (optionalKisToken.isPresent()) {
                log.info("기존 {} 토큰이 최신화됩니다. 토큰 값: {}", tokenType, response.accessToken());
                KisToken kisToken = optionalKisToken.get();
                kisToken.updateToken(response.accessToken(), LocalDateTime.parse(response.expiresAt(), formatter));
            } else { // 토큰이 아예 없는 경우 -> save
                log.info("새로 {} 토큰이 발급됩니다. 토큰 값: {}", tokenType, response.accessToken());
                KisToken kisToken = KisToken.builder()
                        .tokenType(tokenType)
                        .tokenValue(response.accessToken())
                        .expiredAt(LocalDateTime.parse(response.expiresAt(), formatter))
                        .build();

                tokenRepository.save(kisToken);
            }
        }
        return response;
    }

    /***
     * 웹소켓 접속키(approval_key)를 발급한다.
     * <p>
     * access_token과 달리 저장하지 않는다. 접속키는 세션 연결 시 초기 1회만 쓰이고,
     * 인증이 끝난 뒤에는 세션이 끊기지 않는 한 유효기간(24시간)이 지나도 수신에 영향을 주지 않는다.
     * 다시 필요해지는 시점은 재접속뿐이고, 그때는 새로 발급받으면 되므로 DB에 넣어도 재사용할 구간이 없다.
     *
     * @param restBaseUrl 발급 요청을 보낼 KIS REST 주소 (실전/모의 도메인이 다르다)
     * @param appKey      앱키
     * @param appSecret   앱시크릿
     * @return approval_key 값
     */
    @Transactional(propagation = Propagation.SUPPORTS) // 외부 HTTP 호출뿐이라 DB 커넥션을 쥐고 있을 이유가 없다
    public String issueApprovalKey(String restBaseUrl, String appKey, String appSecret) {

        TokenDto.ApprovalRequest request = TokenDto.ApprovalRequest.builder()
                .grantType("client_credentials")
                .appKey(appKey)
                .secretKey(appSecret)
                .build();

        TokenDto.ApprovalResponse response;
        try {
            response = RestClient.create(restBaseUrl).post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/oauth2/Approval")
                            .build())
                    .header("content-type", "application/json; charset=utf-8")
                    .body(request)
                    .retrieve()
                    .body(TokenDto.ApprovalResponse.class);
        } catch (RestClientException e) {
            // 접속키 값은 물론 앱키도 로그에 남기지 않는다.
            log.error("웹소켓 접속키 발급 실패", e);
            throw new TokenException(TokenErrorCode.APPROVAL_KEY_FAILED);
        }

        if (response == null || !StringUtils.hasText(response.approvalKey())){
            log.error("웹소켓 접속키 발급 응답이 비어 있습니다.");
            throw new TokenException(TokenErrorCode.APPROVAL_KEY_FAILED);
        }

        log.info("웹소켓 접속키 발급 성공");
        return response.approvalKey();
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
        // 현재 시간 + 10분이 토큰의 만료시간 이전이라면, true (기존 토큰 사용) 반환
        LocalDateTime nowPlus10Min = LocalDateTime.now().plusMinutes(10);

        return nowPlus10Min.isBefore(kisToken.getExpiredAt());
    }
}
