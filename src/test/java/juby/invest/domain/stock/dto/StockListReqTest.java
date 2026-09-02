package juby.invest.domain.stock.dto;

import juby.invest.domain.stock.enums.Order;
import juby.invest.domain.stock.enums.StockSortBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("종목 목록 정렬 (StockListReq#toComparator)")
class StockListReqTest {

    /*
     * 네 컬럼이 서로 다른 순서를 만들도록 값을 구성한다.
     * 엉뚱한 Comparator가 선택되면 반드시 순서가 어긋나도록 하기 위함이다.
     *
     *   종목명   ASC : 삼성전자, 카카오, 현대차
     *   종가     ASC : 카카오, 삼성전자, 현대차
     *   등락률   ASC : 현대차, 삼성전자, 카카오
     *   거래대금 ASC : 카카오, 현대차, 삼성전자
     */
    private static final List<StockListDto.StockList> STOCKS = List.of(
            StockListDto.StockList.of("005930", "삼성전자",  70_800,  0.71, 2_500_000_000_000L),
            StockListDto.StockList.of("035720", "카카오",    41_500,  3.20,   800_000_000_000L),
            StockListDto.StockList.of("005380", "현대차",   250_000, -1.43, 1_200_000_000_000L));

    private List<String> sortedNames(StockSortBy sortBy, Order order) {
        return STOCKS.stream()
                .sorted(new StockListDto.StockListReq(sortBy, order).toComparator())
                .map(StockListDto.StockList::stockName)
                .toList();
    }

    @DisplayName("4개 컬럼 x 2개 방향으로 정렬된다")
    @ParameterizedTest(name = "[{index}] {0} {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            " STOCK_NAME    | ASC  | 삼성전자, 카카오, 현대차 ",
            " STOCK_NAME    | DESC | 현대차, 카카오, 삼성전자 ",
            " CLOSE_PRICE   | ASC  | 카카오, 삼성전자, 현대차 ",
            " CLOSE_PRICE   | DESC | 현대차, 삼성전자, 카카오 ",
            " FLUCTUATE     | ASC  | 현대차, 삼성전자, 카카오 ",
            " FLUCTUATE     | DESC | 카카오, 삼성전자, 현대차 ",
            " TRADING_VALUE | ASC  | 카카오, 현대차, 삼성전자 ",
            " TRADING_VALUE | DESC | 삼성전자, 현대차, 카카오 ",
    })
    void sortsByEveryColumnAndDirection(StockSortBy sortBy, Order order, String expected) {

        assertThat(sortedNames(sortBy, order)).containsExactly(expected.split("\\s*,\\s*"));
    }

    @Nested
    @DisplayName("기본 정렬 정책")
    class DefaultPolicy {

        @Test
        @DisplayName("파라미터가 모두 없으면 종목명 오름차순이다")
        void defaultsToStockNameAsc() {
            StockListDto.StockListReq req = new StockListDto.StockListReq(null, null);

            assertThat(req.sortBy()).isEqualTo(StockSortBy.STOCK_NAME);
            assertThat(req.order()).isEqualTo(Order.ASC);
            assertThat(sortedNames(null, null)).containsExactly("삼성전자", "카카오", "현대차");
        }

        @Test
        @DisplayName("거래대금은 방향을 주지 않으면 내림차순이다")
        void tradingValueDefaultsToDesc() {
            StockListDto.StockListReq req = new StockListDto.StockListReq(StockSortBy.TRADING_VALUE, null);

            assertThat(req.order()).isEqualTo(Order.DESC);
            assertThat(sortedNames(StockSortBy.TRADING_VALUE, null))
                    .containsExactly("삼성전자", "현대차", "카카오");
        }

        @DisplayName("거래대금 외 나머지는 방향을 주지 않으면 오름차순이다")
        @ParameterizedTest(name = "{0} -> ASC")
        @EnumSource(value = StockSortBy.class, names = "TRADING_VALUE", mode = EnumSource.Mode.EXCLUDE)
        void othersDefaultToAsc(StockSortBy sortBy) {

            assertThat(new StockListDto.StockListReq(sortBy, null).order()).isEqualTo(Order.ASC);
        }
    }

    @Nested
    @DisplayName("2차 정렬 (동점 처리)")
    class Tiebreaker {

        // 종가가 같고 종목코드만 다른 두 종목
        private final List<StockListDto.StockList> tied = List.of(
                StockListDto.StockList.of("000660", "가나전자", 50_000, 1.0, 100L),
                StockListDto.StockList.of("000030", "나다전자", 50_000, 1.0, 100L));

        private List<String> sortedCodes(Order order) {
            return tied.stream()
                    .sorted(new StockListDto.StockListReq(StockSortBy.CLOSE_PRICE, order).toComparator())
                    .map(StockListDto.StockList::stockCode)
                    .toList();
        }

        @Test
        @DisplayName("1차 기준이 같으면 종목코드 오름차순으로 정렬한다")
        void breaksTieByStockCodeAsc() {
            assertThat(sortedCodes(Order.ASC)).containsExactly("000030", "000660");
        }

        @Test
        @DisplayName("내림차순이어도 2차 정렬은 종목코드 오름차순을 유지한다")
        void tiebreakerStaysAscendingUnderDesc() {
            // reversed()가 1차 기준에만 적용되고 thenComparing은 그 뒤에 붙으므로 방향이 뒤집히지 않는다.
            assertThat(sortedCodes(Order.DESC)).containsExactly("000030", "000660");
        }
    }

    @Test
    @DisplayName("종목명 정렬은 유니코드 순서라 영문 종목명이 한글보다 앞에 온다")
    void latinNamesPrecedeHangul() {
        List<StockListDto.StockList> stocks = List.of(
                StockListDto.StockList.of("005930", "삼성전자",   70_800,  0.71, 1L),
                StockListDto.StockList.of("000660", "SK하이닉스", 178_000, 1.20, 1L),
                StockListDto.StockList.of("005380", "현대차",    250_000, -1.43, 1L));

        List<String> sorted = stocks.stream()
                .sorted(new StockListDto.StockListReq(StockSortBy.STOCK_NAME, Order.ASC).toComparator())
                .map(StockListDto.StockList::stockName)
                .toList();

        // String.compareTo는 UTF-16 코드 단위 비교이므로 'S'(U+0053) < '삼'(U+C0BC)
        assertThat(sorted).containsExactly("SK하이닉스", "삼성전자", "현대차");
    }
}