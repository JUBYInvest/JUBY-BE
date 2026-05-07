package juby.invest.domain.pinecone.service;

import io.pinecone.clients.Index;
import io.pinecone.configs.PineconeConfig;
import io.pinecone.configs.PineconeConnection;
import juby.invest.domain.news.dto.NewsResDto;
import juby.invest.domain.news.service.NewsService;
import juby.invest.domain.pinecone.converter.PineconeConverter;
import juby.invest.domain.pinecone.dto.PineconeResDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.db_data.client.ApiException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PineconeService {

    private final PineconeConfig pineconeConfig;
    private final PineconeConverter pineconeConverter;
    private final NewsService newsService;

    /***
     * 함수 기능: 1. 주기적으로 종목 리스트를 순회하며, 네이버 뉴스 API를 호출한다.
     *          2. 받은 응답을 vectorDB에 넣을 수 있게끔 컨버터를 통해 변환한다.
     *          3. 변환된 최종 응답을 vectorDB에 삽입한다.
     * @param query 종목 100개 리스트 (ex) 삼성전자, SK하이닉스, 현대자동차...)
     * @throws ApiException pinecone 호출 예외
     */
    public PineconeResDto.PineconeSuccess upsertData(String query) throws ApiException {

        pineconeConfig.setHost("juby-lovh45p.svc.aped-4627-b74a.pinecone.io");
        PineconeConnection connection = new PineconeConnection(pineconeConfig);

        Index index = new Index(pineconeConfig, connection, "juby");

        List<Map<String, String>> upsertRecords = pineconeConverter.makeUpsertRecords(newsService.callNewsApi(query));

        index.upsertRecords("naver_news", upsertRecords);

        return PineconeResDto.PineconeSuccess.builder()
                .upsertTime(LocalDateTime.now())
                .build();
    }
}
