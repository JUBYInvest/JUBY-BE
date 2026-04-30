package juby.invest.global.kisapi.market.service;

import juby.invest.global.kisapi.market.dto.PeriodStockPriceDto;
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
public class PeriodStockPriceService {

    private final RestClient investRestClient;
    private final TokenService tokenService;

    @Value("${kis.mock.app-key}") String appKey;
    @Value("${kis.mock.app-secret}") String appSecret;

    public List<PeriodStockPriceDto.Output> getPeriodStockPrice(String stockCode, String startDate, String endDate){

        String accessToken = tokenService.getMockAccessToken().accessToken();

        PeriodStockPriceDto response = investRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_INPUT_DATE_1", startDate)
                        .queryParam("FID_INPUT_DATE_2", endDate)
                        .queryParam("FID_PERIOD_DIV_CODE", "D")
                        .queryParam("FID_ORG_ADJ_PRC", "0")
                        .build())
                .header("content-type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST03010100")
                .retrieve()
                .body(PeriodStockPriceDto.class);

        if (response != null && response.rtCd().equals("0")){
            log.info("국내주식기간별시세조회API 호출성공");
        }
        else {
            log.error("국내주식기간별시세조회API 호출실패: {}", response == null ? "호출 오류" : response.message());
        }
        return response.output();
    }
}
