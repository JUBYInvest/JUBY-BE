package juby.invest.domain.marketindex.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class MarketIndexDto {

    /***
     * @param asOf    응답을 만든 시각. 프론트가 "N분 전" 같은 신선도 표시를 할 수 있게 함께 내린다.
     * @param indices 지수 목록. 조회에 실패한 지수는 빠지므로 항목 수가 줄어들 수 있다.
     */
    public record MarketIndexRes(
            LocalDateTime asOf,
            List<Item> indices
    ){
        public static MarketIndexRes of(LocalDateTime asOf, List<Item> indices){
            return new MarketIndexRes(asOf, indices);
        }
    }

    /***
     * @param code            업종코드 (코스피 0001, 코스닥 1001)
     * @param name            표시명
     * @param currentIndex    현재 지수. 소수 둘째 자리까지 내려오므로 BigDecimal이다.
     * @param changeRate      전일 대비율 %. KIS가 계산해 준 값이다.
     * @param accTradingValue 누적 거래대금. 값이 비어 있으면 null이다.
     * @param realtime        실시간 체결로 받은 값인지 여부. false면 KIS 현재지수 REST 폴백이다.
     */
    @Builder
    public record Item(
            String code,
            String name,
            BigDecimal currentIndex,
            BigDecimal changeRate,
            BigDecimal accTradingValue,
            boolean realtime
    ){}
}
