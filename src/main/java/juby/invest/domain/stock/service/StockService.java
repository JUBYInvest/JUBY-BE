package juby.invest.domain.stock.service;

import juby.invest.domain.kis.market.dto.CurrentPriceRes;
import juby.invest.domain.kis.market.service.MarketService;
import juby.invest.domain.news.enums.NewsSortType;
import juby.invest.domain.pinecone.dto.PineconeDto;
import juby.invest.domain.pinecone.service.PineconeService;
import juby.invest.domain.stock.converter.StockConverter;
import juby.invest.domain.stock.dto.StockDetailDto;
import juby.invest.domain.stock.dto.StockNewsDto;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.enums.Period;
import juby.invest.domain.stock.exception.StockException;
import juby.invest.domain.stock.exception.code.StockErrorCode;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final MarketService marketService;
    private final PineconeService pineconeService;

    private static final LocalDate START_OF_THE_DATE = LocalDate.of(2025, 1, 2);
    private static final int PAGE_SIZE = 10;
    // LATEST 정렬 기준: 발행일 내림차순 -> ID 내림차순
    private static final Comparator<PineconeDto.StockNewsHit> LATEST_FIRST =
            Comparator.comparing(
                            (PineconeDto.StockNewsHit hit) -> StockConverter.toPublishedAt(hit.pubDate()),
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(PineconeDto.StockNewsHit::id, Comparator.reverseOrder());

    /***
     * 함수 기능: 종목 상세 정보 (Period 기간의 OHLCV 데이터, 전일 대비 변동률)를 제공한다.
     * @param stockCode 종목 코드
     * @param period 기간
     * @return StockDetailRes (종목이름, 종목코드, 전일대비 변동률, 기간, OHLCV 리스트)
     */
    public StockDetailDto.StockDetailRes getStockDetails(String stockCode, Period period) throws InterruptedException {

        // 주식명을 구하기 위해 stockCode를 통해 Stock 객체를 찾는다.
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new StockException(StockErrorCode.STOCK_NOT_FOUND));

        // 현재가와 전일 대비 변동률을 KIS API를 통해 가져온다.
        CurrentPriceRes.Info kisResponse = marketService.getDailyPrice(stockCode);
        int currentPrice = Integer.parseInt(kisResponse.currentPrice());
        double comparePrev = Double.parseDouble(kisResponse.dayChange());

        // 최신 거래일을 기준으로 역산하여 시작일을 계산한다.
        LocalDate recentTradeDay = dailyPriceRepository.findMaxDateByStock(stock);
        LocalDate startDate = calculateDay(recentTradeDay, period);

        // 시작일 ~ 오늘까지 DailyPrice 객체 -> DailyPrices DTO
        List<StockDetailDto.DailyPrices> dailyPrices = dailyPriceRepository.findAllByStockAndDateGreaterThanEqualOrderByDateAsc(stock, startDate).stream()
                .map(StockConverter::toDailyPrices)
                .toList();

        return StockDetailDto.StockDetailRes.of(stock.getStockName(), stockCode, currentPrice, comparePrev, period, dailyPrices);
    }

    /***
     * 함수 기능: 1. 종목코드로 종목명을 찾아 vectorDB에서 해당 종목의 뉴스 후보 (100개)를 조회한다.
     *          2. 요청한 정렬 기준으로 정렬한다.
     *          3. page로 해당 구간을 잘라 반환한다.
     * @param stockNewsReq NewsSortType, page
     * @return StockNewRes 종목의 뉴스 데이터
     */
    public StockNewsDto.StockNewsRes getStockNews(String stockCode, StockNewsDto.StockNewsReq stockNewsReq) {

        NewsSortType sort = stockNewsReq.sort();
        int page = stockNewsReq.page();

        // stockCode를 통해 종목을 찾는다.
        Stock stock = stockRepository.findById(stockCode)
                .orElseThrow(() -> new StockException(StockErrorCode.STOCK_NOT_FOUND));

        // stockCode에 해당하는 뉴스데이터 리스트를 반환한다.
        List<PineconeDto.StockNewsHit> hits = pineconeService.searchStockNews(stock.getStockName());

        // 정렬기준이 LATEST일 경우에만 발행일 기준으로 다시 정렬한다.
        // 이때 hits는 불변 리스트이기에, 새로운 불변 리스트를 하나 더 만든다.
        if (sort == NewsSortType.LATEST){
            hits = hits.stream().sorted(LATEST_FIRST).toList();
        }

        int startIndex = Math.min(page * PAGE_SIZE, hits.size());
        int endIndex = Math.min(startIndex + PAGE_SIZE, hits.size());

        List<StockNewsDto.NewsItem> newsList = hits.subList(startIndex, endIndex).stream()
                .map(StockConverter::toStockNewsItem)
                .toList();

        return StockNewsDto.StockNewsRes.of(stockCode, stock.getStockName(), sort, newsList, page, hits.size());
    }

    // 오늘날짜를 기준으로 역산하여 시작일을 계산한다.
    private LocalDate calculateDay(LocalDate recentTradeDay, Period period) {
        return switch (period) {
            case Period.ONE_WEEK -> recentTradeDay.minusDays(7);
            case Period.ONE_MONTH -> recentTradeDay.minusMonths(1);
            case Period.THREE_MONTH -> recentTradeDay.minusMonths(3);
            case Period.SIX_MONTH -> recentTradeDay.minusMonths(6);
            case Period.ONE_YEAR -> recentTradeDay.minusYears(1);
            case Period.THREE_YEAR -> recentTradeDay.minusYears(3);
            default -> START_OF_THE_DATE;
        };
    }
}
