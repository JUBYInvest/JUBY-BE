package juby.invest.domain.news.converter;

import juby.invest.domain.news.dto.NewsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

@Slf4j
public class NewsConverter {

    /***
     * 함수 기능: 네이버 뉴스 API의 응답을 받아, html 태그, ;& 등 불필요한 문자를 제거한다.
     * @param response 네이버 뉴스 API의 응답
     * @return 태그가 제거된 응답 본문
     */
    public static NewsDto.NaverNewsRes cleanResponse(NewsDto.NaverNewsRes response){
        List<NewsDto.ItemDetail> cleanedItemList = response.itemList().stream()
                .map(item -> NewsDto.ItemDetail.builder()
                        .title(cleanHtml(item.title()))
                        .originallink(item.originallink())
                        .description(cleanHtml(item.description()))
                        .pubDate(item.pubDate())
                        .build())
                .toList();

        return NewsDto.NaverNewsRes.builder()
                .display(response.display())
                .itemList(cleanedItemList)
                .build();
    }

    // 불필요 문자 추출
    private static String cleanHtml(String html){
        String noTag = html.replaceAll("<[^>]*>", "");
        return HtmlUtils.htmlUnescape(noTag);
    }
}
