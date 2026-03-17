package juby.invest.initiate;

import juby.invest.dto.PeriodStockPriceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KsiApiClient {

    private final RestClient investRestClient;

    @Value("${kis.mock.app-key}") String appKey;
    @Value("${kis.mock.app-secret}") String appSecret;

    /***
     * 함수 기능: 국내주식기간별시세 API 호출/응답으로 받은 Output List를 savePeriodStockPrice()에 넘겨준다.
     * @param stockCode 주식코드(6자리)
     * @param startDate 조회시작날짜
     * @param endDate 조회마지막날짜
     * @return List<PeriodStockPriceDto.Output> 해당 주식 코드의 일자별 OHLVC가 담긴다.
     */
    public List<PeriodStockPriceDto.Output> getPeriodStockPrice(String stockCode, String startDate, String endDate, String accessToken){

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
            log.info("국내주식기간별시세 API 출력 정상");
        }
        else {
            log.info("국내주식기간별시세 API 출력 오류 {}", response == null ? "null" : response.message());
            throw new RuntimeException("국내주식기간별시세 API 출력 오류");
        }

        return response.output();
    }
}
