package juby.invest.domain.kis.realtime.dto;

import juby.invest.domain.marketindex.enums.MarketIndex;

import java.math.BigDecimal;

/***
 * 브라우저로 밀어 보내는 시장 지수 이벤트.
 *
 * @param code         업종코드 (코스피 0001, 코스닥 1001)
 * @param name         표시명
 * @param currentIndex 현재 지수
 * @param changeRate   전일 대비율 %
 */
public record IndexEvent(
        String code,
        String name,
        BigDecimal currentIndex,
        BigDecimal changeRate
) {
    public static IndexEvent from(MarketIndex marketIndex, RealtimeIndexTick tick) {
        return new IndexEvent(
                marketIndex.getCode(),
                marketIndex.getLabel(),
                tick.currentIndex(),
                tick.changeRate());
    }
}
