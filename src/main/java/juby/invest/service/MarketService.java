package juby.invest.service;

import juby.invest.dto.CurrentPriceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {

    @Value("${kis.app-secret}") String appSecret;
    @Value("${kis.app-key}") String appKey;

    private final RestClient investRestClient;
    private final TokenService tokenService;

    public CurrentPriceDto.Output getCurrentPrice(String stockCode){

        // accessToken 발급 과정
        String accessToken = tokenService.getAccessToken().accessToken();

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

        return new CurrentPriceDto.Output(response.output().currentPrice(), response.output().compareYesterday());
    }
}
