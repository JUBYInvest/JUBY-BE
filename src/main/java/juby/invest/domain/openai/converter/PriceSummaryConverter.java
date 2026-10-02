package juby.invest.domain.openai.converter;

import juby.invest.domain.stock.converter.StockConverter;
import juby.invest.domain.stock.entity.DailyPrice;

import java.util.Comparator;
import java.util.List;

/***
 * 클래스 기능: DB에 저장된 종목의 최근 일봉 데이터를 챗봇 프롬프트의 <price> 섹션에 넣을 텍스트로 변환한다.
 *            일별 종가 데이터이므로 실시간 시세가 아니라는 점과 기준일을 함께 명시한다.
 */
public class PriceSummaryConverter {

    private PriceSummaryConverter() {
    }

    /***
     * 함수 기능: 최근 일봉 목록으로 기준일 시세, 전일 대비 등락률, 조회 구간 등락률/최고가/최저가를 요약한다.
     * @param recentPrices 최근 일봉 목록 (최신순, 첫 번째가 기준일)
     * @return 프롬프트용 요약 텍스트. 데이터가 없으면 안내 문구
     */
    public static String toSummary(List<DailyPrice> recentPrices) {
        if (recentPrices == null || recentPrices.isEmpty()) {
            return "해당 종목의 일별 주가 데이터가 아직 준비되지 않았습니다.";
        }

        DailyPrice latest = recentPrices.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("(일별 종가 기준이며 실시간 시세가 아님. 기준일: %s)\n".formatted(latest.getDate()));

        // 전일 데이터가 없으면 등락률을 0%로 오해하지 않도록 "정보 없음"으로 표기한다.
        String dayChange = recentPrices.size() < 2
                ? "전일 데이터 없음"
                : "전일 대비 " + toSignedPercent(
                        StockConverter.calculateFluctuation(latest.getClosePrice(), recentPrices.get(1).getClosePrice()));
        sb.append("- 기준일 종가: %s (%s)\n".formatted(toWon(latest.getClosePrice()), dayChange));
        sb.append("- 기준일 시가/고가/저가: %s / %s / %s\n".formatted(
                toWon(latest.getOpenPrice()), toWon(latest.getHighPrice()), toWon(latest.getLowPrice())));
        sb.append("- 기준일 거래량: %,d주".formatted(latest.getVolume()));

        if (recentPrices.size() >= 2) {
            DailyPrice oldest = recentPrices.get(recentPrices.size() - 1);
            DailyPrice highest = recentPrices.stream().max(Comparator.comparing(DailyPrice::getHighPrice)).orElseThrow();
            DailyPrice lowest = recentPrices.stream().min(Comparator.comparing(DailyPrice::getLowPrice)).orElseThrow();
            String range = "최근 %d거래일(%s ~ %s)".formatted(recentPrices.size(), oldest.getDate(), latest.getDate());

            sb.append("\n- %s 등락률: %s".formatted(range, toSignedPercent(
                    StockConverter.calculateFluctuation(latest.getClosePrice(), oldest.getClosePrice()))));
            sb.append("\n- %s 최고가/최저가: %s(%s) / %s(%s)".formatted(range,
                    toWon(highest.getHighPrice()), highest.getDate(),
                    toWon(lowest.getLowPrice()), lowest.getDate()));
        }
        return sb.toString();
    }

    // 70800 -> "70,800원"
    private static String toWon(Integer price) {
        return "%,d원".formatted(price);
    }

    // 0.71 -> "+0.71%", -1.43 -> "-1.43%"
    private static String toSignedPercent(double percent) {
        return "%+.2f%%".formatted(percent);
    }
}