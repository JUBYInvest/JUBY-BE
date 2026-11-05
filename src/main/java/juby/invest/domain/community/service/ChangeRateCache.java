package juby.invest.domain.community.service;

import juby.invest.domain.kis.market.service.MarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/***
 * 커뮤니티 목록/종목 카드에 보여줄 종목별 전일 대비 등락률을 잠깐 보관한다.
 * 목록을 열 때마다 종목마다 KIS 현재가 API를 부르면 호출 제한에 걸리므로, 한 번 가져온 값은 TTL 동안 재사용한다.
 * 종목 수가 stock 테이블 크기(상위 종목)로 한정되므로 만료된 항목을 따로 지우지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeRateCache {

    static final Duration TTL = Duration.ofMinutes(5);
    // 실패한 종목은 짧게만 기억해, KIS 장애 중에 요청마다 다시 부르지 않으면서도 빨리 복구되게 한다.
    static final Duration FAILURE_TTL = Duration.ofMinutes(1);

    private final MarketService marketService;
    private final Map<String, ChangeRate> cache = new ConcurrentHashMap<>();

    /***
     * @param rate 전일 대비 등락률(%). 조회에 실패했으면 null
     * @param fetchedAt KIS에서 가져온 시각
     */
    public record ChangeRate(Double rate, LocalDateTime fetchedAt) {
        boolean isFresh(LocalDateTime now) {
            return fetchedAt.plus(rate == null ? FAILURE_TTL : TTL).isAfter(now);
        }
    }

    /***
     * 함수 기능: 종목들의 등락률을 조회한다. 캐시에 없거나 만료된 종목만 KIS에서 새로 가져온다.
     * @param stockCodes 종목 코드들 (중복 가능)
     * @return 종목 코드별 등락률
     */
    public Map<String, ChangeRate> get(Collection<String> stockCodes) {
        Map<String, ChangeRate> result = new HashMap<>();
        for (String stockCode : new LinkedHashSet<>(stockCodes)) {
            result.put(stockCode, get(stockCode));
        }
        return result;
    }

    private ChangeRate get(String stockCode) {
        LocalDateTime now = LocalDateTime.now();
        ChangeRate cached = cache.get(stockCode);
        if (cached != null && cached.isFresh(now)) {
            return cached;
        }
        // 같은 종목을 동시에 새로 가져오는 일이 드물게 생길 수 있지만, 결과가 같으므로 잠그지 않는다.
        ChangeRate fresh = new ChangeRate(fetch(stockCode), now);
        cache.put(stockCode, fresh);
        return fresh;
    }

    private Double fetch(String stockCode) {
        try {
            return Double.valueOf(marketService.getDailyPrice(stockCode).dayChange());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException e) {
            // 등락률은 부가 정보라 목록 응답은 실패시키지 않는다. 장애가 길어지면 MarketService 쪽 로그로 드러난다.
            log.warn("커뮤니티 등락률 조회 실패: stockCode={}, reason={}", stockCode, e.toString());
            return null;
        }
    }
}
