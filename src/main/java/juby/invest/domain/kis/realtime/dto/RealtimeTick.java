package juby.invest.domain.kis.realtime.dto;

/***
 * KIS 실시간 체결가(H0STCNT0) 1건을 우리가 쓰는 값만 담은 DTO.
 * <p>
 * KIS가 내려주는 46개 필드 중 목록·상세 화면에 필요한 것만 추린다.
 * 전일대비율은 KIS가 계산해서 주므로 장중에는 전일 종가를 따로 조회할 필요가 없다.
 *
 * @param stockCode        종목코드 (MKSC_SHRN_ISCD)
 * @param contractTime     체결시간 HHmmss. 날짜가 없으므로 신선도 판단에는 쓰지 않는다. (STCK_CNTG_HOUR)
 * @param currentPrice     현재가 (STCK_PRPR)
 * @param changeRate       전일대비율 % (PRDY_CTRT)
 * @param openPrice        시가 (STCK_OPRC)
 * @param highPrice        고가 (STCK_HGPR)
 * @param lowPrice         저가 (STCK_LWPR)
 * @param accVolume        누적거래량 (ACML_VOL)
 * @param accTradingValue  누적거래대금 (ACML_TR_PBMN)
 */
public record RealtimeTick(
        String stockCode,
        String contractTime,
        int currentPrice,
        double changeRate,
        int openPrice,
        int highPrice,
        int lowPrice,
        long accVolume,
        long accTradingValue
) {}
