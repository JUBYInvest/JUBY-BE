package juby.invest.domain.backtest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import juby.invest.domain.backtest.enums.BacktestPeriod;
import juby.invest.domain.backtest.enums.LeadingStockTheme;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class LeadingStockResDto {

    public record LeadingStockRes(

            @Schema(description = "전략 표시명", example = "SMA 이동평균선 전략")
            String strategyName,

            @Schema(description = "기간 프리셋 코드", example = "ONE_YEAR")
            BacktestPeriod period,

            @Schema(description = "기간 표시용 라벨", example = "1년")
            String periodLabel,

            @Schema(description = "백테스트 시작일", example = "2025-10-02")
            LocalDate startDate,

            @Schema(description = "백테스트 종료일", example = "2026-10-02")
            LocalDate endDate,

            @Schema(description = "프리셋 마지막 적재 시각", example = "2026-10-10T04:04:11")
            LocalDateTime updatedAt,

            @Schema(description = "테마별 대장주 목록")
            List<LeadingStock> leadingStocks
    ){}

    @Builder
    public record LeadingStock(

            String stockCode, // 종목 코드
            LeadingStockTheme theme, // 테마 코드
            String themeLabel, // 테마 라벨
            String stockName, // 종목명
            BigDecimal returnPercentage, // 누적 수익률
            int tradeCount // 거래 횟수
    ){}
}
