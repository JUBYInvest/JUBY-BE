package juby.invest.api.market.service;

import juby.invest.api.market.dto.HolidayDto;
import juby.invest.api.token.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckHolidayService {

    @Value("${kis.real.app-key}") String appKey;
    @Value("${kis.real.app-secret}") String appSecret;

    private final RestClient realInvestRestClient;
    private final TokenService tokenService;

    public List<HolidayDto.Output> getHolidayList(String baseDate){

        String accessToken = tokenService.getRealAccessToken().accessToken();

        HolidayDto response = realInvestRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/chk-holiday")
                        .queryParam("BASS_DT", baseDate)
                        .queryParam("CTX_AREA_NK", "")
                        .queryParam("CTX_AREA_FK", "")
                        .build())
                .header("content-type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "CTCA0903R")
                .retrieve()
                .body(HolidayDto.class);

        if (response != null && response.rtCd().equals("0")){
            log.info("국내휴장일조회 API 호출 성공");
        }
        else {
            log.info("국내휴장일조회 API 호출 실패 {}", response == null ? "null" : response.message());
            throw new RuntimeException("국내휴장일조회 API 호출 실패");
        }

        return response.output();
    }
}
