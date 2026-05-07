package juby.invest.domain.news.service;

import juby.invest.domain.news.converter.NewsConverter;
import juby.invest.domain.news.dto.NewsResDto;
import juby.invest.domain.news.exception.NewsException;
import juby.invest.domain.news.exception.code.NewsErrorCode;
import juby.invest.domain.pinecone.service.PineconeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.db_data.client.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    private final RestClient newsSearchRestClient;
    private final NewsConverter newsConverter;

    /***
     * 함수 기능: 1. 전달받은 파라미터 값을 포함해서 네이버 뉴스 검색 API에 요청을 보낸다.
     * @param query 검색어
     * @return html 태그와 ;& 문자가 제거된 응답 본문
     */
    public NewsResDto.NewsResponse callNewsApi(String query){

        NewsResDto.NewsResponse response = newsSearchRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("query", query)
                        .build())
                .retrieve()
                .body(NewsResDto.NewsResponse.class);

        if (response == null){
            throw new NewsException(NewsErrorCode.NOT_FOUND);
        }

        return newsConverter.cleanResponse(response);
    }
}
