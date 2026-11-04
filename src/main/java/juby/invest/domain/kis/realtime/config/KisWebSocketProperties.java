package juby.invest.domain.kis.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

/***
 * KIS 실시간(웹소켓) 세션용 자격증명 목록. (application.yaml의 kis.ws)
 * <p>
 * appkey 1개당 웹소켓 세션 1개, 세션 1개당 실시간 등록 41건이 상한이다.
 * 100종목을 모두 구독하려면 자격증명이 3개(실전 1 + 모의 2) 필요하다.
 * <p>
 * 기존 REST 경로(MarketService, TokenService)가 쓰는 kis.mock.* / kis.real.* 설정은 그대로 둔다.
 * 웹소켓만 목록 구조가 필요하므로 별도 prefix로 분리했다.
 */
@ConfigurationProperties(prefix = "kis.ws")
public record KisWebSocketProperties(
        List<Credential> credentials
) {
    public KisWebSocketProperties {
        credentials = (credentials == null) ? List.of() : List.copyOf(credentials);
    }

    /***
     * 세션 1개를 띄우는 데 필요한 자격증명.
     *
     * @param name         로그 식별자. 세션이 3개라 어느 세션의 로그인지 구분할 수단이 필요하다. (appkey는 절대 로깅하지 않는다)
     * @param appKey       앱키
     * @param appSecret    앱시크릿
     * @param restBaseUrl  접속키(approval_key) 발급용 REST 주소. 실전/모의 도메인이 다르다.
     * @param wsUrl        웹소켓 주소. 실전 21000, 모의 31000 포트.
     */
    public record Credential(
            String name,
            String appKey,
            String appSecret,
            String restBaseUrl,
            String wsUrl
    ) {
        /***
         * 앱키가 채워져 있는지 확인한다.
         * 아직 발급받지 못한 자격증명은 .env에서 빈 값으로 두므로, 그 세션만 건너뛸 수 있어야 한다.
         */
        public boolean isConfigured() {
            return StringUtils.hasText(appKey)
                    && StringUtils.hasText(appSecret)
                    && StringUtils.hasText(restBaseUrl)
                    && StringUtils.hasText(wsUrl);
        }
    }
}
