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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    private static final int DISPLAY_COUNT = 20; //20개 뉴스 검색
    private static final int MAX_RETRY = 3; // 429(Too Many Requests) 발생 시 최대 재시도 횟수
    private static final long BASE_DELAY_MS = 500; // 재시도 대기시간 산정 기준(지수 백오프)

    private final RestClient newsSearchRestClient;
    private final NewsConverter newsConverter;

    /***
     * 함수 기능: 1. 전달받은 파라미터 값을 포함해서 네이버 뉴스 검색 API에 요청을 보낸다.
     *          2. 429(Too Many Requests) 응답을 받으면 지수 백오프로 대기 후 최대 MAX_RETRY회 재시도한다.
     * @param query 검색어
     * @return html 태그와 ;& 문자가 제거된 응답 본문
     */
    public NewsResDto.NewsResponse callNewsApi(String query){

        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                NewsResDto.NewsResponse response = newsSearchRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .queryParam("query", query)
                                .queryParam("display", DISPLAY_COUNT)
                                .build())
                        .retrieve()
                        .body(NewsResDto.NewsResponse.class);

                if (response == null){
                    throw new NewsException(NewsErrorCode.NOT_FOUND);
                }

                return newsConverter.cleanResponse(response);

            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt == MAX_RETRY){
                    log.error("네이버 뉴스 API 호출 제한(429) 재시도 {}회 모두 실패. query={}", MAX_RETRY, query);
                    throw new NewsException(NewsErrorCode.TOO_MANY_REQUESTS);
                }

                long backoffMs = BASE_DELAY_MS * (1L << attempt);
                log.warn("네이버 뉴스 API 호출 제한(429) 발생. {}ms 후 재시도 ({}/{}). query={}",
                        backoffMs, attempt + 1, MAX_RETRY, query);
                sleep(backoffMs);
            }
        }

        throw new NewsException(NewsErrorCode.TOO_MANY_REQUESTS);
    }

    private void sleep(long millis){
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NewsException(NewsErrorCode.TOO_MANY_REQUESTS);
        }
    }
}
