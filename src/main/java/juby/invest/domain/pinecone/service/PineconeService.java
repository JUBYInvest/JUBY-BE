package juby.invest.domain.pinecone.service;

import io.pinecone.clients.Index;
import juby.invest.domain.news.dto.NewsResDto;
import juby.invest.domain.news.service.NewsService;
import juby.invest.domain.pinecone.converter.PineconeConverter;
import juby.invest.domain.pinecone.dto.PineconeDto;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.db_data.client.ApiException;
import org.openapitools.db_data.client.model.Hit;
import org.openapitools.db_data.client.model.SearchRecordsResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PineconeService {

    // Pinecone integrated-embedding upsert_records 1회 호출당 최대 레코드 수 제한(96건)에 맞춘 청크 크기
    private static final int UPSERT_CHUNK_SIZE = 96;
    // 네이버 뉴스 API 초당 호출 제한(429)을 예방하기 위한 종목 간 호출 간격
    private static final long NAVER_API_CALL_DELAY_MS = 200;

    private final Index pineconeConfig;
    private final PineconeConverter pineconeConverter;
    private final NewsService newsService;
    private final StockRepository stockRepository;

    /***
     * 함수 기능: 1. 주기적으로 종목 리스트를 순회하며, 네이버 뉴스 API를 호출한다.
     *          2. 받은 응답을 vectorDB에 넣을 수 있게끔 컨버터를 통해 변환한다.
     *          3. 변환된 최종 응답을 vectorDB에 삽입한다.
     * @param query 종목 검색어
     * @throws ApiException pinecone 호출 예외
     */
    public PineconeDto.UpsertSuccess upsertData(String query) throws ApiException {

        NewsResDto.NewsResponse newsResponse = newsService.callNewsApi(query);
        List<Map<String, String>> upsertRecords = pineconeConverter.makeUpsertRecords(newsResponse, query);

        try {
            pineconeConfig.upsertRecords("naver_news", upsertRecords);
        } catch (ApiException e){
            log.error("vectorDB upsert 실패. query={}, 레코드 {}건", query, upsertRecords.size(), e);
            throw e;
        }

        return PineconeDto.UpsertSuccess.builder()
                .newsResponse(newsResponse)
                .upsertTime(LocalDateTime.now())
                .build();
    }

    /***
     * 함수 기능: 1. DB에 저장된 전체 종목(기본 100개)을 순회하며 각 종목명을 query로 뉴스를 조회한다.
     *          2. 조회된 레코드를 종목 단위로 upsert하지 않고 청크(UPSERT_CHUNK_SIZE)로 모아 upsertRecords 호출 횟수를 줄인다.
     *          3. 종목별 조회/청크 upsert 중 예외가 발생해도 나머지 종목 적재는 계속 진행한다.
     * @return 전체/성공 건수와 실패한 종목명 목록
     */
    public PineconeDto.BulkUpsertSuccess upsertAllStockNews(){

        List<Stock> stocks = stockRepository.findAll(); // 전체 종목 리스트를 뽑는다.
        List<String> failedStocks = new ArrayList<>();
        int successCount = 0;

        List<Map<String, String>> buffer = new ArrayList<>();
        List<String> pendingStocks = new ArrayList<>();

        // 종목 리스트 전체 순회하여, 뉴스를 호출하고 Pinecone DB에 Upsert한다.
        for (Stock stock : stocks){
            try {
                // 해당 stockName으로 검색한 뉴스 20개가 NewsResponse Dto에 담겨진다.
                NewsResDto.NewsResponse newsResponse = newsService.callNewsApi(stock.getStockName());

                // NewsResponse에 담긴 20개 뉴스를 Pinecone DB에 Upsert한다.
                buffer.addAll(pineconeConverter.makeUpsertRecords(newsResponse, stock.getStockName()));
                pendingStocks.add(stock.getStockName());
            } catch (Exception e){
                log.error("종목 [{}] 뉴스 조회 실패", stock.getStockName(), e);
                failedStocks.add(stock.getStockName());
                continue;
            } finally {
                sleep(NAVER_API_CALL_DELAY_MS); // 네이버 API 초당 호출 제한 예방
            }

            if (buffer.size() >= UPSERT_CHUNK_SIZE){
                successCount += flushChunk(buffer, pendingStocks, failedStocks);
            }
        }

        if (!buffer.isEmpty()){
            successCount += flushChunk(buffer, pendingStocks, failedStocks);
        }

        return PineconeDto.BulkUpsertSuccess.builder()
                .totalCount(stocks.size())
                .successCount(successCount)
                .failedStocks(failedStocks)
                .upsertTime(LocalDateTime.now())
                .build();
    }

    /***
     * 함수 기능: 버퍼에 모인 레코드를 Pinecone upsert_records 배치 제한(UPSERT_CHUNK_SIZE)에 맞춰 나눠 호출하고, 버퍼/대기 목록을 초기화한다.
     * @param buffer 청크 단위로 모은 upsert 대상 레코드 (UPSERT_CHUNK_SIZE를 넘을 수 있음)
     * @param pendingStocks buffer에 기여한 종목명 목록 (성공 시 successCount에 반영, 실패 시 failedStocks에 반영)
     * @param failedStocks 실패한 종목명을 누적할 목록
     * @return 이번 청크 upsert로 성공 처리된 종목 수
     */
    private int flushChunk(List<Map<String, String>> buffer, List<String> pendingStocks, List<String> failedStocks){
        int flushedStockCount = pendingStocks.size();
        boolean allSucceeded = true;

        // buffer가 UPSERT_CHUNK_SIZE(96)를 넘길 수 있으므로, Pinecone 배치 제한을 지키기 위해 항상 재슬라이싱해서 호출한다.
        for (int i = 0; i < buffer.size(); i += UPSERT_CHUNK_SIZE){
            List<Map<String, String>> subBatch = buffer.subList(i, Math.min(i + UPSERT_CHUNK_SIZE, buffer.size()));

            try {
                pineconeConfig.upsertRecords("naver_news", new ArrayList<>(subBatch));
            } catch (ApiException e){
                log.error("뉴스 청크 upsert 실패 (레코드 {}건)", subBatch.size(), e);
                allSucceeded = false;
            }
        }

        if (!allSucceeded){
            failedStocks.addAll(pendingStocks);
            flushedStockCount = 0;
        }

        buffer.clear();
        pendingStocks.clear();

        return flushedStockCount;
    }

    private void sleep(long millis){
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /***
     * 함수 기능: 1. 사용자의 질문 요청을 vectorDB에서 조회한다.
     *          2. 받은 데이터를 Dto로 변환한다.
     *          3. AI에게 넘겨준다.
     * @param question 질문 내용
     * @param stockName 필터: 종목이름
     * @throws ApiException pinecone 예외처리
     */
    public PineconeDto.SearchSuccess searchData(String question, String stockName) throws ApiException {

        // 검색할 내용들
        List<String> fields = new ArrayList<>();
        fields.add("title");
        fields.add("description");
        fields.add("pubDate");
        fields.add("stock_name");

        // 필터
        Map<String, Object> filter = new HashMap<>();
        filter.put("stock_name", stockName);

        // 응답 반환 및 정보 분리
        SearchRecordsResponse recordsResponse;
        try {
            recordsResponse = pineconeConfig.searchRecordsByText(question, "naver_news", fields, 3, filter, null);
        } catch (ApiException e){
            log.error("vectorDB 검색 실패. question={}, stockName={}", question, stockName, e);
            throw e;
        }
        log.info("recordsResposne = {}", recordsResponse);

        List<Hit> hits = recordsResponse.getResult().getHits();
        List<PineconeDto.SearchSuccess.News> newsList = new ArrayList<>();

        for (Hit hit : hits){
            Map<String, Object> resFields = (Map<String, Object>) hit.getFields();

            String title = resFields.get("title").toString();
            String description = resFields.get("description").toString();
            String pubDate = resFields.get("pubDate").toString();

            newsList.add(PineconeDto.SearchSuccess.News.builder()
                    .stockName(stockName)
                    .title(title)
                    .description(description)
                    .pubDate(pubDate)
                    .build());
        }

        return PineconeDto.SearchSuccess.builder()
                .newsList(newsList)
                .build();
    }
}
