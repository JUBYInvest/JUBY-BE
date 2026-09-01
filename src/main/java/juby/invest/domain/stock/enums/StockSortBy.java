package juby.invest.domain.stock.enums;

import juby.invest.domain.stock.dto.StockListDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;

@Getter
@RequiredArgsConstructor
public enum StockSortBy {
    STOCK_NAME(Order.ASC, Comparator.comparing(StockListDto.StockList::stockName)),
    CLOSE_PRICE(Order.ASC, Comparator.comparing(StockListDto.StockList::closePrice)),
    FLUCTUATE(Order.ASC, Comparator.comparing(StockListDto.StockList::fluctuate)),
    TRADING_VALUE(Order.DESC, Comparator.comparing(StockListDto.StockList::tradingValue));

    private final Order defaultOrder;
    private final Comparator<StockListDto.StockList> comparator;
}
