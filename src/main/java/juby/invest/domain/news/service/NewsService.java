package juby.invest.domain.news.service;

import juby.invest.domain.news.dto.NewsResDto;
import juby.invest.domain.news.exception.NewsException;
import juby.invest.domain.news.exception.code.NewsErrorCode;
import juby.invest.domain.pinecone.service.PineconeService;
import lombok.RequiredArgsConstructor;
import org.openapitools.db_data.client.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final RestClient newsSearchRestClient;

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

        return cleanResponse(response);
    }

    public NewsResDto.NewsResponse cleanResponse(NewsResDto.NewsResponse response){
        List<NewsResDto.ItemDetail> cleanedItemList = response.itemList().stream()
                .map(item -> NewsResDto.ItemDetail.builder()
                        .title(cleanHtml(item.title()))
                        .originallink(item.originallink())
                        .description(cleanHtml(item.description()))
                        .pubDate(item.pubDate())
                        .build())
                .toList();

        return NewsResDto.NewsResponse.builder()
                .display(response.display())
                .itemList(cleanedItemList)
                .build();
    }

    private String cleanHtml(String html){
        String noTag = html.replaceAll("<[^>]*>", "");
        return HtmlUtils.htmlUnescape(noTag);
    }
}
