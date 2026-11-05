package juby.invest.domain.kis.realtime.dto;

/***
 * 브라우저로 밀어 보내는 종목 시세 이벤트.
 * <p>
 * 조회 API의 StockList와 따로 두는 이유: 저 DTO에는 사용자별 값인 isLiked가 들어 있다.
 * 스트림은 인증 없이 열리는 공개 경로라 사용자별 값을 실을 수 없고, 관심 등록 여부는
 * 실시간으로 바뀌는 값도 아니다(초기 REST 응답에 이미 들어 있다).
 *
 * @param stockCode       종목코드
 * @param currentPrice    현재가
 * @param changeRate      전일 대비율 %
 * @param accTradingValue 누적 거래대금
 * @param contractTime    체결시간 HHmmss
 */
public record PriceEvent(
        String stockCode,
        int currentPrice,
        double changeRate,
        long accTradingValue,
        String contractTime
) {
    public static PriceEvent from(RealtimeTick tick) {
        return new PriceEvent(
                tick.stockCode(),
                tick.currentPrice(),
                tick.changeRate(),
                tick.accTradingValue(),
                tick.contractTime());
    }
}
