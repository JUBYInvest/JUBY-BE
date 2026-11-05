package juby.invest.domain.kis.realtime.dto;

import java.math.BigDecimal;

/***
 * KIS 국내지수 실시간체결(H0UPCNT0) 1건을 우리가 쓰는 값만 담은 DTO.
 * <p>
 * 지수는 2,543.21처럼 소수 둘째 자리까지 내려오므로 정수로 받으면 값이 깨진다. 전부 BigDecimal로 받는다.
 * 누적거래량/거래대금도 BigDecimal인 이유: 정수로 올 것이라 단정했다가 소수가 섞이면 파싱이 터져
 * 해당 레코드가 통째로 버려진다. 표시용 값이라 정밀도를 그대로 들고 있는 편이 안전하다.
 *
 * @param indexCode        업종코드 (BSTP_CLS_CODE). 코스피 0001, 코스닥 1001
 * @param businessHour     영업시간 HHmmss. 날짜가 없으므로 신선도 판단에는 쓰지 않는다. (BSOP_HOUR)
 * @param currentIndex     현재가 지수 (PRPR_NMIX)
 * @param changeRate       전일 대비율 % (PRDY_CTRT)
 * @param openIndex        시가 지수 (OPRC_NMIX)
 * @param highIndex        최고가 지수 (NMIX_HGPR)
 * @param lowIndex         최저가 지수 (NMIX_LWPR)
 * @param accVolume        누적 거래량 (ACML_VOL)
 * @param accTradingValue  누적 거래대금 (ACML_TR_PBMN)
 */
public record RealtimeIndexTick(
        String indexCode,
        String businessHour,
        BigDecimal currentIndex,
        BigDecimal changeRate,
        BigDecimal openIndex,
        BigDecimal highIndex,
        BigDecimal lowIndex,
        BigDecimal accVolume,
        BigDecimal accTradingValue
) {}
