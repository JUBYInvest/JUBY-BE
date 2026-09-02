package juby.invest.domain.stock.service;

import juby.invest.domain.kis.market.dto.CurrentPriceRes;
import juby.invest.domain.kis.market.service.MarketService;
import juby.invest.domain.news.enums.NewsSortType;
import juby.invest.domain.pinecone.dto.PineconeDto;
import juby.invest.domain.pinecone.service.PineconeService;
import juby.invest.domain.stock.converter.StockConverter;
import juby.invest.domain.stock.dto.StockDetailDto;
import juby.invest.domain.stock.dto.StockListDto;
import juby.invest.domain.stock.dto.StockNewsDto;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.enums.Order;
import juby.invest.domain.stock.enums.Period;
import juby.invest.domain.stock.enums.StockSortBy;
import juby.invest.domain.stock.exception.StockException;
import juby.invest.domain.stock.exception.code.StockErrorCode;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * 함수 기능: DB에 저장된 최신 날짜를 기준으로 전 종목의 종가/등락률/거래대금을 조회한다.
     *          스케줄러가 16시에 금일 데이터를 적재하므로, 적재 전에는 직전 거래일 기준으로 조회한다.
     * @return StockListRes 목록 (종목코드, 종목명, 종가, 등락률, 거래대금)
     */
    @Transactional(readOnly = true)
    public StockListDto.StockListRes getStockList(StockListDto.StockListReq stockListReq) {

        // 가장 최신 날짜와 기준일의 전 날짜
        LocalDate baseDate = dailyPriceRepository.findMaxDate();
        if (baseDate == null){
            throw new StockException(StockErrorCode.DAILYPRICE_NOT_FOUND);
        }
        LocalDate prevDate = dailyPriceRepository.findMaxDateBefore(baseDate);

        List<DailyPrice> dailyPrices = dailyPriceRepository.findAllByDateWithStock(baseDate);

        // 기준일 전날의 종가 데이터를 Map으로 변환
        Map<String, Integer> prevClosePrices = (prevDate == null)
                ? Map.of()
                : dailyPriceRepository.findAllByDateWithStock(prevDate).stream()
                  .collect(Collectors.toMap(
                          dp -> dp.getStock().getStockCode(),
                          DailyPrice::getClosePrice,
                          (existing, duplicate) -> existing));

        List<StockListDto.StockList> stockList = dailyPrices.stream()
                .map(dp -> StockListDto.StockList.of(
                        dp.getStock().getStockCode(),
                        dp.getStock().getStockName(),
                        dp.getClosePrice(),
                        calculateFluctuation(dp.getClosePrice(), prevClosePrices.get(dp.getStock().getStockCode())),
                        dp.getTradingValue() == null ? 0L : dp.getTradingValue()))
                .sorted(stockListReq.toComparator())
                .toList();

        return StockListDto.StockListRes.of(baseDate, stockList);
    }

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

        // 최신 거래일을 구한다.
        LocalDate recentTradeDay = dailyPriceRepository.findMaxDateByStock(stock);

        // 시작일 ~ 오늘까지 DailyPrice 객체 -> DailyPrices DTO
        List<StockDetailDto.DailyPrices> dailyPrices = (recentTradeDay == null)
                // 최신 거래일이 없을 경우 빈 리스트를 반환한다.
                ? List.of()
                // 최신 거래일을 기준으로 역산하여 시작일을 계산한다.
                : dailyPriceRepository.findAllByStockAndDateGreaterThanEqualOrderByDateAsc(stock, calculateDay(recentTradeDay, period)).stream()
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

    // 가장 최근 날짜와 날짜의 전일의 변동률을 계산한다.
    double calculateFluctuation(Integer closePrice, Integer prevClosePrice) {

        if (prevClosePrice == null || prevClosePrice == 0){
            return 0.0;
        }

        // 변동률은 소수 둘째 자리까지
        return Math.round((double) (closePrice - prevClosePrice) / prevClosePrice * 10000) / 100.0;
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
