package juby.invest.domain.member.converter;

import juby.invest.domain.member.dto.LikeStockListDto;
import juby.invest.domain.member.entity.LikeStock;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LikeStockConverter")
class LikeStockConverterTest {

    private final Member member = Member.builder().id(1L).build();
    private final Stock stock = Stock.builder().stockCode("005930").stockName("삼성전자").build();
    private final LikeStock likeStock = LikeStock.builder().member(member).stock(stock).build();

    @Test
    @DisplayName("기준일/전일 가격이 모두 있으면 등락률까지 계산해 반환한다")
    void convertsWithFluctuation() {
        DailyPrice basePrice = DailyPrice.builder()
                .stock(stock).date(LocalDate.of(2026, 9, 3)).closePrice(70800).tradingValue(1_000_000L).build();
        DailyPrice prevPrice = DailyPrice.builder()
                .stock(stock).date(LocalDate.of(2026, 9, 2)).closePrice(70300).build();

        LikeStockListDto.LikeStockList result = LikeStockConverter.convertToLikeStockList(
                likeStock, Map.of("005930", basePrice), Map.of("005930", prevPrice));

        assertThat(result.closePrice()).isEqualTo(70800);
        assertThat(result.fluctuate()).isEqualTo(0.71);
        assertThat(result.tradingValue()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("기준일에 일봉이 없으면(신규 상장 등) 가격 관련 필드를 모두 null로 반환한다")
    void returnsNullPriceFieldsWhenBasePriceMissing() {
        LikeStockListDto.LikeStockList result = LikeStockConverter.convertToLikeStockList(
                likeStock, Map.of(), Map.of());

        assertThat(result.stockCode()).isEqualTo("005930");
        assertThat(result.stockName()).isEqualTo("삼성전자");
        assertThat(result.closePrice()).isNull();
        assertThat(result.fluctuate()).isNull();
        assertThat(result.tradingValue()).isNull();
        assertThat(result.likedAt()).isEqualTo(likeStock.getLikedAt());
    }

    @Test
    @DisplayName("전일 일봉만 없으면(첫 거래일 등) 등락률은 0.0, 나머지 가격 필드는 정상 반환한다")
    void returnsZeroFluctuationWhenPrevPriceMissing() {
        DailyPrice basePrice = DailyPrice.builder()
                .stock(stock).date(LocalDate.of(2026, 9, 3)).closePrice(70000).tradingValue(500L).build();

        LikeStockListDto.LikeStockList result = LikeStockConverter.convertToLikeStockList(
                likeStock, Map.of("005930", basePrice), Map.of());

        assertThat(result.closePrice()).isEqualTo(70000);
        assertThat(result.fluctuate()).isZero();
        assertThat(result.tradingValue()).isEqualTo(500L);
    }
}