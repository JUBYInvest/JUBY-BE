package juby.invest.domain.pinecone.service;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.clients.Index;
import io.pinecone.configs.PineconeConfig;
import io.pinecone.configs.PineconeConnection;
import io.pinecone.proto.QueryRequest;
import io.pinecone.proto.QueryResponse;
import io.pinecone.proto.ScoredVector;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import juby.invest.domain.news.dto.NewsResDto;
import juby.invest.domain.news.service.NewsService;
import juby.invest.domain.pinecone.converter.PineconeConverter;
import juby.invest.domain.pinecone.dto.PineconeResDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.db_data.client.ApiException;
import org.openapitools.db_data.client.model.Hit;
import org.openapitools.db_data.client.model.SearchRecordsResponse;
import org.openapitools.db_data.client.model.SearchRecordsResponseResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PineconeService {

    private final Index pineconeConfig;
    private final PineconeConverter pineconeConverter;
    private final NewsService newsService;

    /***
     * 함수 기능: 1. 주기적으로 종목 리스트를 순회하며, 네이버 뉴스 API를 호출한다.
     *          2. 받은 응답을 vectorDB에 넣을 수 있게끔 컨버터를 통해 변환한다.
     *          3. 변환된 최종 응답을 vectorDB에 삽입한다.
     * @param query 종목 검색어
     * @throws ApiException pinecone 호출 예외
     */
    public PineconeResDto.UpsertSuccess upsertData(String query) throws ApiException {

        NewsResDto.NewsResponse newsResponse = newsService.callNewsApi(query);
        List<Map<String, String>> upsertRecords = pineconeConverter.makeUpsertRecords(newsResponse, query);

        pineconeConfig.upsertRecords("naver_news", upsertRecords);

        return PineconeResDto.UpsertSuccess.builder()
                .newsResponse(newsResponse)
                .upsertTime(LocalDateTime.now())
                .build();
    }

    /***
     * 함수 기능: 1. 사용자의 질문 요청을 vectorDB에서 조회한다.
     *          2. 받은 데이터를 Dto로 변환한다.
     *          3. AI에게 넘겨준다.
     * @param question 질문 내용
     * @param stockName 필터: 종목이름
     * @throws ApiException pinecone 예외처리
     */
    public PineconeResDto.SearchSuccess searchData(String question, String stockName) throws ApiException {

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
        SearchRecordsResponse recordsResponse = pineconeConfig.searchRecordsByText(question, "naver_news", fields, 3, filter, null);
        log.info("recordsResposne = {}", recordsResponse);

        List<Hit> hits = recordsResponse.getResult().getHits();
        List<PineconeResDto.SearchSuccess.News> newsList = new ArrayList<>();

        for (Hit hit : hits){
            Map<String, Object> resFields = (Map<String, Object>) hit.getFields();

            String title = resFields.get("title").toString();
            String description = resFields.get("description").toString();
            String pubDate = resFields.get("pubDate").toString();

            newsList.add(PineconeResDto.SearchSuccess.News.builder()
                    .stockName(stockName)
                    .title(title)
                    .description(description)
                    .pubDate(pubDate)
                    .build());
        }

        return PineconeResDto.SearchSuccess.builder()
                .newsList(newsList)
                .build();
    }
}
