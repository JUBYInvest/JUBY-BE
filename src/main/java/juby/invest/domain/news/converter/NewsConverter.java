package juby.invest.domain.news.converter;

import juby.invest.domain.news.dto.NewsResDto;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

@Component
public class NewsConverter {

    /***
     * 함수 기능: 네이버 뉴스 API의 응답을 받아, html 태그, ;& 등 불필요한 문자를 제거한다.
     * @param response 네이버 뉴스 API의 응답
     * @return 태그가 제거된 응답 본문
     */
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
