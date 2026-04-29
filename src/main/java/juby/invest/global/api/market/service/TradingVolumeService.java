package juby.invest.global.api.market.service;

import juby.invest.global.api.market.dto.TradingVolumeDto;
import juby.invest.global.api.token.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingVolumeService {

    private final RestClient realInvestRestClient;
    private final TokenService tokenService;

    @Value("${kis.real.app-key}") String appKey;
    @Value("${kis.real.app-secret}") String appSecret;

    public List<TradingVolumeDto.Output> getTradingVolume(){
        String accessToken = tokenService.getRealAccessToken().accessToken();

        TradingVolumeDto response = realInvestRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/volume-rank")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_COND_SCR_DIV_CODE", "20171")
                        .queryParam("FID_INPUT_ISCD", "0000")
                        .queryParam("FID_BLNG_CLS_CODE", "3")
                        .queryParam("FID_TRGT_CLS_CODE", "111111111")
                        .queryParam("FID_TRGT_EXLS_CLS_CODE", "0000000000")
                        .queryParam("FID_INPUT_PRICE_1", "10000")
                        .queryParam("FID_INPUT_PRICE_2", "")
                        .queryParam("FID_VOL_CNT", "")
                        .queryParam("FID_INPUT_DATE_1", "")
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHPST01710000")
                .retrieve()
                .body(TradingVolumeDto.class);

        if (response == null || response.rtCd().equals("1")){
            String errMsg = response != null ? response.message() : "응답이 NULL";
            log.info("거래량 조회 실패: 사유: {}", errMsg);
            throw new RuntimeException("거래량 조회를 실패하였습니다.");
        }

        log.info("거래량 조회 성공: response.outputList 반환 {}", response.outputList());
        return response.outputList();
    }
}
