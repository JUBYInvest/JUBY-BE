package juby.invest.domain.pinecone.converter;

import juby.invest.domain.news.dto.NewsDto;
import juby.invest.domain.pinecone.dto.PineconeDto;
import org.openapitools.db_data.client.model.Hit;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class PineconeConverter {

    /***
     * 함수 기능: 네이버 뉴스 API 응답을 pinecone의 naver_news 네임스페이스에 넣기 위해 알맞은 레코드 형태로 변환한다.
     *          originallink는 종목 뉴스 조회 API에서 언론사명을 추출하는데 사용한다.
     *          혹여 비어있을 때를 대비해, null 대신 빈 문자열로 대체한다.
     * @param naverNewsResponse 네이버 뉴스 API 응답
     * @return vectorDB에 넣을 레코드 리스트
     */
    public static List<Map<String, String>> toNaverNewsNameSpaceRecord(NewsDto.NaverNewsRes naverNewsResponse, String stockName){

        List<Map<String, String>> upsertRecords = new ArrayList<>();

        for (NewsDto.ItemDetail item : naverNewsResponse.itemList()){
            Map<String, String> record = new HashMap<>();

            record.put("_id", toRecordId(item, stockName));
            record.put("title", item.title());
            record.put("description", item.description());
            record.put("pubDate", item.pubDate());
            record.put("text", item.title() + item.description());
            record.put("stock_name", stockName);
            record.put("originallink", Objects.toString(item.originallink(), ""));

            upsertRecords.add(record);
        }
        return upsertRecords;
    }

    // Pinecone에 들어갈 _id 필드 생성
    private static String toRecordId(NewsDto.ItemDetail item, String stockName) {

        // item의 originallink가 비어있으면, title으로 대체한다.
        String originallink = item.originallink();
        String seed = (originallink == null || originallink.isBlank()) ? item.title() : item.originallink();
        
        return UUID.nameUUIDFromBytes((seed + "#" + stockName).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /***
     * Pinecone에서 반환된 뉴스 레코드를 StockNewsHit DTO로 변환한다.
     */
    public static PineconeDto.StockNewsHit toStockNewsHit(Hit hit){
        
        // Pinecone에서 Semantic Search를 통해 찾은 뉴스 레코드 ex) key:value = title:뉴스제목
        Map<String, Object> resFields = (Map<String, Object>) hit.getFields();

        return PineconeDto.StockNewsHit.builder()
                .id(hit.getId())
                .score(hit.getScore())
                .title(fieldAsString(resFields, "title"))
                .description(fieldAsString(resFields, "description"))
                .originalLink(fieldAsString(resFields, "originallink"))
                .pubDate(fieldAsString(resFields, "pubDate"))
                .build();
    }
    
    // 메타데이터 필드가 null일 경우 빈 문자열 반환
    private static String fieldAsString(Map<String, Object> resFields, String key){
        Object value = (resFields == null) ? null : resFields.get(key);
        return (value == null) ? "": value.toString();
    }
}
