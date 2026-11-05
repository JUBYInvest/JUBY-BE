package juby.invest.domain.marketindex.converter;

import juby.invest.domain.kis.market.dto.IndexPriceRes;
import juby.invest.domain.kis.realtime.dto.RealtimeIndexTick;
import juby.invest.domain.marketindex.dto.MarketIndexDto;
import juby.invest.domain.marketindex.enums.MarketIndex;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/***
 * 지수 1건을 응답 항목으로 변환한다. 실시간 수신 값과 REST 응답을 같은 모양으로 맞춘다.
 */
@Slf4j
public class MarketIndexConverter {

    /*** 실시간 체결로 받은 지수. 값이 이미 BigDecimal이라 파싱할 것이 없다. */
    public static MarketIndexDto.Item toItem(MarketIndex marketIndex, RealtimeIndexTick tick) {
        return MarketIndexDto.Item.builder()
                .code(marketIndex.getCode())
                .name(marketIndex.getLabel())
                .currentIndex(tick.currentIndex())
                .changeRate(tick.changeRate())
                .accTradingValue(tick.accTradingValue())
                .realtime(true)
                .build();
    }

    /***
     * KIS 현재지수 REST 응답. 모든 값이 문자열이라 숫자로 바꿔야 한다.
     * <p>
     * 현재 지수는 없으면 항목 자체가 의미 없으므로 파싱 실패를 그대로 올려보낸다(호출 쪽이 그 지수를 제외한다).
     * 등락률·거래대금은 비어 와도 지수는 보여줄 수 있으므로 null로 떨어뜨린다.
     */
    public static MarketIndexDto.Item toItem(MarketIndex marketIndex, IndexPriceRes.Info info) {
        return MarketIndexDto.Item.builder()
                .code(marketIndex.getCode())
                .name(marketIndex.getLabel())
                .currentIndex(new BigDecimal(info.currentIndex().trim()))
                .changeRate(toBigDecimalOrNull(info.changeRate()))
                .accTradingValue(toBigDecimalOrNull(info.accTradingValue()))
                .realtime(false)
                .build();
    }

    private static BigDecimal toBigDecimalOrNull(String value) {

        if (value == null || value.isBlank()){
            return null;
        }

        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e){
            log.warn("지수 응답의 숫자 값을 읽을 수 없습니다. 값: {}", value);
            return null;
        }
    }
}
