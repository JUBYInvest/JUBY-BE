package juby.invest.domain.kis.market.service;

import juby.invest.domain.kis.market.dto.*;
import juby.invest.domain.kis.market.exception.MarketException;
import juby.invest.domain.kis.market.exception.code.MarketErrorCode;
import juby.invest.domain.kis.token.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MarketService {

    @Value("${kis.real.app-key}") String realAppKey;
    @Value("${kis.real.app-secret}") String realAppSecret;
    @Value("${kis.mock.app-secret}") String mockAppSecret;
    @Value("${kis.mock.app-key}") String mockAppKey;

    private final RestClient mockInvestRestClient;
    private final RestClient realInvestRestClient;
    private final TokenService tokenService;

    /***
     * 함수 기능: 주식현재가 시세 조회 API
     * @param stockCode 종목코드(ex 005930)
     * @return 응답DTO
     */
    public CurrentPriceRes.Info getDailyPrice(String stockCode) throws InterruptedException {

        // accessToken 발급 과정
        String accessToken = tokenService.getRealAccessToken().accessToken();

        CurrentPriceRes response = realInvestRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode) // 입력 종목 코드
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appKey", realAppKey)
                .header("appsecret", realAppSecret)
                .header("tr_id", "FHKST01010100")
                .retrieve()
                .body(CurrentPriceRes.class);

        if (response == null || !response.rtCd().equals("0") || response.output() == null){
            log.info("현재가 조회 실패");
            throw new MarketException(MarketErrorCode.DAILY_PRICE_FAILED);
        }

        log.info("현재가 조회 성공: response.output 반환 {}", response.output());
        return CurrentPriceRes.Info.builder()
                .openPrice(response.output().openPrice())
                .highPrice(response.output().highPrice())
                .lowPrice(response.output().lowPrice())
                .currentPrice(response.output().currentPrice())
                .volume(response.output().volume())
                .compareYesterday(response.output().compareYesterday())
                .compareYesterdaySign(response.output().compareYesterdaySign())
                .dayChange(response.output().dayChange())
                .build();
    }

    /***
     * 함수 기능: 국내 휴장일 조회 API
     * @param baseDate 기준일자
     * @return 응답DTO
     */
    public List<HolidayDto.Output> getHolidayList(String baseDate) throws InterruptedException {

        String accessToken = tokenService.getRealAccessToken().accessToken();

        HolidayDto response = realInvestRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/chk-holiday")
                        .queryParam("BASS_DT", baseDate) // 기준일자
                        .queryParam("CTX_AREA_NK", "") // 연속조회키
                        .queryParam("CTX_AREA_FK", "") // 연속조회검색조건
                        .build())
                .header("content-type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", realAppKey)
                .header("appsecret", realAppSecret)
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

    /***
     * 함수 기능: 주식 현재가 일자별 조회 API
     * @param stockCode 종목코드 (ex 005930)
     * @return 응답 DTO
     */
    public List<DailyPriceRes.DailyInfo> getDailyStockPrice(String stockCode) throws InterruptedException {
        String accessToken = tokenService.getMockAccessToken().accessToken();

        DailyPriceRes response = mockInvestRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_PERIOD_DIV_CODE", "D")
                        .queryParam("FID_ORG_ADJ_PRC", 1)
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", mockAppKey)
                .header("appsecret", mockAppSecret)
                .header("tr_id", "FHKST01010400")
                .retrieve()
                .body(DailyPriceRes.class);

        if (response == null){
            log.info("주식현재가 일자별 API 호출 실패");
            throw new RuntimeException("주식현재가 일자별 API 호출 실패");
        }

        log.info("주식현재가 일자별 API 호출 성공");
        return response.output();
    }

    /***
     * 함수 기능: 국내주식기간별 시세 조회 API
     * @param stockCode 종목코드 (ex 005930)
     * @param startDate 조회 시작일자
     * @param endDate 조회 종료일자 (최대 100개)
     * @return
     */
    public List<PeriodDailyPriceDto.Output> getPeriodStockPrice(String stockCode, String startDate, String endDate) throws InterruptedException {

        String accessToken = tokenService.getMockAccessToken().accessToken();

        PeriodDailyPriceDto response = mockInvestRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_INPUT_DATE_1", startDate)
                        .queryParam("FID_INPUT_DATE_2", endDate)
                        .queryParam("FID_PERIOD_DIV_CODE", "D") // D:일봉 W:주봉 M:월봉 Y:년봉
                        .queryParam("FID_ORG_ADJ_PRC", "0") // 수정주가 원주가 가격 여부. 0: 수정주가 1: 원주가
                        .build())
                .header("content-type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", mockAppKey)
                .header("appsecret", mockAppSecret)
                .header("tr_id", "FHKST03010100")
                .retrieve()
                .body(PeriodDailyPriceDto.class);

        if (response != null && response.rtCd().equals("0")){
            log.info("국내주식기간별시세조회API 호출성공");
        }
        else {
            log.error("국내주식기간별시세조회API 호출실패: {}", response == null ? "호출 오류" : response.message());
        }
        return response.output();
    }

    /***
     * 함수 기능: 거래량순위 조회 API
     * @return
     */
    public List<TradingVolumeDto.Output> getTradingVolume() throws InterruptedException {
        String accessToken = tokenService.getRealAccessToken().accessToken();

        TradingVolumeDto response = realInvestRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/volume-rank")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J") // 조건 시장 분류코드 J(KRX), NX(NXT)
                        .queryParam("FID_COND_SCR_DIV_CODE", "20171") // 조건 화면 분류코드 20171
                        .queryParam("FID_INPUT_ISCD", "0000") // 입력 종목코드. 0000(전체) 기타(업종코드)
                        .queryParam("FID_DIV_CLS_CODE", "0") // 분류 구분코드. 0(전체) 1(보통주) 2(우선주)
                        .queryParam("FID_BLNG_CLS_CODE", "3") // 소속 구분코드. 3(거래금액순)
                        .queryParam("FID_TRGT_CLS_CODE", "111111111") // 대상 구분코드
                        .queryParam("FID_TRGT_EXLS_CLS_CODE", "0000001100") // 대상 제외구분코드
                        .queryParam("FID_INPUT_PRICE_1", "") // 입력가격1: 가격~
                        .queryParam("FID_INPUT_PRICE_2", "") // 입력가격2: ~가격
                        .queryParam("FID_VOL_CNT", "") // 거래량 수: 거래량 ~
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", realAppKey)
                .header("appsecret", realAppSecret)
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
