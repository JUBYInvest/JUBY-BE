package juby.invest.domain.openai.converter;

import juby.invest.domain.stock.entity.DailyPrice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PriceSummaryConverter 챗봇 주가 요약")
class PriceSummaryConverterTest {

    private static DailyPrice price(String date, int open, int high, int low, int close, int volume) {
        return DailyPrice.builder()
                .date(LocalDate.parse(date))
                .openPrice(open).highPrice(high).lowPrice(low).closePrice(close).volume(volume)
                .build();
    }

    @Test
    @DisplayName("기준일 시세와 전일 대비/구간 등락률, 구간 최고가/최저가를 요약한다")
    void summarizeRecentPrices() {
        // 최신순
        List<DailyPrice> prices = List.of(
                price("2026-10-01", 70300, 71000, 69800, 70800, 12_345_678),
                price("2026-09-30", 69500, 72000, 69000, 70300, 10_000_000),
                price("2026-09-29", 68500, 69800, 68000, 69000, 9_000_000));

        String summary = PriceSummaryConverter.toSummary(prices);

        assertThat(summary)
                .contains("실시간 시세가 아님", "기준일: 2026-10-01")
                .contains("기준일 종가: 70,800원 (전일 대비 +0.71%)")
                .contains("기준일 시가/고가/저가: 70,300원 / 71,000원 / 69,800원")
                .contains("기준일 거래량: 12,345,678주")
                // (70800 - 69000) / 69000 = +2.608...%
                .contains("최근 3거래일(2026-09-29 ~ 2026-10-01) 등락률: +2.61%")
                .contains("최고가/최저가: 72,000원(2026-09-30) / 68,000원(2026-09-29)");
    }

    @Test
    @DisplayName("하락하면 음수 부호로 표시한다")
    void showNegativeSign() {
        List<DailyPrice> prices = List.of(
                price("2026-10-01", 70000, 70000, 68500, 69000, 1),
                price("2026-09-30", 70000, 70500, 69500, 70000, 1));

        assertThat(PriceSummaryConverter.toSummary(prices)).contains("전일 대비 -1.43%");
    }

    @Test
    @DisplayName("하루치 데이터뿐이면 전일 대비는 '전일 데이터 없음'으로 표기하고 구간 정보는 생략한다")
    void singleDay() {
        String summary = PriceSummaryConverter.toSummary(
                List.of(price("2026-10-01", 70300, 71000, 69800, 70800, 100)));

        assertThat(summary)
                .contains("기준일 종가: 70,800원 (전일 데이터 없음)")
                .doesNotContain("거래일(");
    }

    @Test
    @DisplayName("데이터가 없으면 안내 문구를 반환한다")
    void emptyPrices() {
        assertThat(PriceSummaryConverter.toSummary(List.of()))
                .isEqualTo("해당 종목의 일별 주가 데이터가 아직 준비되지 않았습니다.");
    }
}