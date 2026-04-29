package juby.invest.global.kisapi.market.service;

import juby.invest.global.kisapi.market.dto.CurrentPriceDto;
import juby.invest.global.kisapi.token.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {

    @Value("${kis.mock.app-secret}") String appSecret;
    @Value("${kis.mock.app-key}") String appKey;

    private final RestClient investRestClient;
    private final TokenService tokenService;

    public CurrentPriceDto.Output getCurrentPrice(String stockCode){

        // accessToken 발급 과정
        String accessToken = tokenService.getMockAccessToken().accessToken();

        CurrentPriceDto response = investRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode) // 입력 종목 코드
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appKey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST01010100")
                .retrieve()
                .body(CurrentPriceDto.class);

        if (response == null){
            log.info("현재가 조회 실패");
            throw new RuntimeException("현재가 조회 실패");
        }

        log.info("현재가 조회 성공: response.output 반환 {}", response.output());
        return new CurrentPriceDto.Output(response.output().currentPrice(), response.output().compareYesterday());
    }
}
