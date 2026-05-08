package juby.invest.domain.pinecone.converter;

import juby.invest.domain.news.dto.NewsResDto;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PineconeConverter {

    /***
     * 함수 기능: 네이버 뉴스 API 응답을 vectorDB에 넣기 위해 변환한다.
     * @param newsResponse 네이버 뉴스 API 응답
     * @return vectorDB에 넣을 레코드 리스트
     */
    public List<Map<String, String>> makeUpsertRecords(NewsResDto.NewsResponse newsResponse, String stockName){

        List<Map<String, String>> upsertRecords = new ArrayList<>();

        for (NewsResDto.ItemDetail item : newsResponse.itemList()){
            Map<String, String> record = new HashMap<>();

            record.put("_id", UUID.randomUUID().toString());
            record.put("title", item.title());
            record.put("description", item.description());
            record.put("pubDate", item.pubDate());
            record.put("text", item.title() + item.description());
            record.put("stock_name", stockName);

            upsertRecords.add(record);
        }

        return upsertRecords;
    }
}
