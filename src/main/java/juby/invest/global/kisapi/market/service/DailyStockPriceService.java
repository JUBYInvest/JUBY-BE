package juby.invest.global.kisapi.market.service;

import juby.invest.global.kisapi.market.dto.DailyStockPriceDto;
import juby.invest.global.kisapi.token.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyStockPriceService {

    private final RestClient investRestClient;
    private final TokenService tokenService;

    @Value("${kis.mock.app-key}") String appKey;
    @Value("${kis.mock.app-secret}") String appSecret;

    public List<DailyStockPriceDto.Output> getDailyStockPrice(String stockCode){
        String accessToken = tokenService.getMockAccessToken().accessToken();

        DailyStockPriceDto response = investRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_PERIOD_DIV_CODE", "D")
                        .queryParam("FID_ORG_ADJ_PRC", 1)
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST01010400")
                .retrieve()
                .body(DailyStockPriceDto.class);

        if (response == null){
            log.info("주식현재가 일자별 API 호출 실패");
            throw new RuntimeException("주식현재가 일자별 API 호출 실패");
        }

        log.info("주식현재가 일자별 API 호출 성공");
        return response.output();
    }
}
