package juby.invest.domain.stock.dto;

import lombok.Builder;

public record StockSearchDto() {

    // 종목명 검색(자동완성) 응답 항목
    @Builder
    public record StockSearchItem(
            String stockCode,
            String stockName
    ){}
}
