package juby.invest.domain.stock.converter;

import juby.invest.domain.backtest.dto.LeadingStockResDto;
import juby.invest.domain.backtest.entity.BacktestPresetResult;
import juby.invest.domain.backtest.enums.LeadingStockTheme;
import juby.invest.domain.pinecone.dto.PineconeDto;
import juby.invest.domain.stock.dto.StockDetailDto;
import juby.invest.domain.stock.dto.StockNewsDto;
import juby.invest.domain.stock.dto.StockSearchDto;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Slf4j
public class StockConverter {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 네이버 뉴스 API의 pubDate 형식: "Mon, 11 Aug 2026 14:32:00 +0900"
    private static final DateTimeFormatter PUB_DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;

    // Stock 엔티티를 종목명 검색(자동완성) 응답 항목으로 변환한다.
    public static StockSearchDto.StockSearchItem toStockSearchItem(Stock stock){
        return StockSearchDto.StockSearchItem.builder()
                .stockCode(stock.getStockCode())
                .stockName(stock.getStockName())
                .build();
    }

    /***
     * 함수 기능: DailyPrice 엔티티를 뉴스상세정보조회 API의 응답 DTO로 변환한다.
     * @param dailyPrice DailyPrice 엔티티
     * @return StockDetailDto.DailyPrices
     */
    public static StockDetailDto.DailyPrices toDailyPrices(DailyPrice dailyPrice){
        return StockDetailDto.DailyPrices.builder()
                .date(dailyPrice.getDate())
                .openPrice(dailyPrice.getOpenPrice())
                .highPrice(dailyPrice.getHighPrice())
                .lowPrice(dailyPrice.getLowPrice())
                .closePrice(dailyPrice.getClosePrice())
                .volume(dailyPrice.getVolume())
                .build();
    }

    /***
     * 함수 기능: 네이버 뉴스 API의 pubDate 문자열을 KST 기준 LocalDateTime으로 변환한다.
     *          pubDate는 "Mon, 11 Aug 2026 14:32:00 +0900" 형태라 문자열끼리 비교하면 요일/월 이름 순으로 정렬되어
     *          날짜순이 되지 않는다. 최신순 정렬을 하려면 반드시 날짜 타입으로 바꿔서 비교해야 한다.
     * @param pubDate 네이버 뉴스 API가 준 발행일 문자열
     * @return 변환된 발행일. 형식이 어긋나 파싱에 실패하면 null
     */
    public static LocalDateTime toPublishedAt(String pubDate){

        if (pubDate == null || pubDate.isBlank()){
            return null;
        }

        try {
            return ZonedDateTime.parse(pubDate.trim(), PUB_DATE_FORMATTER)
                    .withZoneSameInstant(KST)
                    .toLocalDateTime();
        } catch (DateTimeParseException e){
            log.warn("뉴스 발행일 파싱 실패. pubDate={}", pubDate);
            return null;
        }
    }

    /***
     * 함수 기능: pinecone에서 조회한 뉴스 레코드 1건을 종목 뉴스 응답 항목으로 변환한다.
     * @param hit pinecone에서 조회한 뉴스 레코드 1건
     * @return StockNewsDto.NewsItem dto
     */
    public static StockNewsDto.NewsItem toStockNewsItem(PineconeDto.StockNewsHit hit) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime publishedAt = toPublishedAt(hit.pubDate());

        return StockNewsDto.NewsItem.builder()
                .timeAgo(toTimeAgo(publishedAt, now))
                .publishedAt(publishedAt)
                .title(hit.title())
                .description(hit.description())
                .originalLink(hit.originalLink())
                .build();
    }

    // 가장 최근 날짜와 날짜의 전일의 변동률을 계산한다.
    public static double calculateFluctuation(Integer closePrice, Integer prevClosePrice) {

        if (prevClosePrice == null || prevClosePrice == 0){
            return 0.0;
        }

        // 변동률은 소수 둘째 자리까지
        return Math.round((double) (closePrice - prevClosePrice) / prevClosePrice * 10000) / 100.0;
    }

    /***
     * 함수 기능: 백테스트 프리셋 결과 1건을 테마별 대장주 응답 항목으로 변환한다.
     * @param theme 대장주 테마
     * @param preset 해당 종목의 백테스트 프리셋 결과
     * @return
     */
    public static LeadingStockResDto.LeadingStock toLeadingStock(LeadingStockTheme theme, BacktestPresetResult preset) {
        return LeadingStockResDto.LeadingStock.builder()
                .stockCode(preset.getStock().getStockCode())
                .theme(theme)
                .themeLabel(theme.getLabel())
                .stockName(preset.getStock().getStockName())
                .returnPercentage(toReturnPercentage(preset.getTotalReturn()))
                .tradeCount(preset.getPositionCount())
                .build();
    }

    // 비율로 저장된 누적 수익률을 퍼센트로 변환한다.
    private static BigDecimal toReturnPercentage(BigDecimal totalReturn) {

        return totalReturn.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    // 발행시각을 "방금 전 / N분 전 / N시간 전 / N일 전" 형태로 바꾼다.
    private static String toTimeAgo(LocalDateTime publishedAt, LocalDateTime now) {
        if (publishedAt == null){
            return "";
        }

        Duration elapsed = Duration.between(publishedAt, now);

        if (elapsed.isNegative()){
            return "방금 전";
        }

        long minutes = elapsed.toMinutes();
        if (minutes < 1) {
            return "방금 전";
        }
        if (minutes < 60){
            return minutes + "분 전";
        }

        long hours = elapsed.toHours();
        if (hours < 24){
            return hours + "시간 전";
        }

        long days = elapsed.toDays();
        return days + "일 전";
    }
}
