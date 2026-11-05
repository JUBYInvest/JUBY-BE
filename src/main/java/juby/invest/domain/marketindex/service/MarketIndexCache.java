package juby.invest.domain.marketindex.service;

import juby.invest.domain.kis.market.dto.IndexPriceRes;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/***
 * KIS 현재지수 REST 응답을 짧게 보관한다.
 * <p>
 * 장중에는 실시간 값이 우선이므로 이 캐시는 주로 <b>장외 시간</b>에 쓰인다. 장외에는 지수가 변하지 않는데,
 * 캐시가 없으면 조회 요청마다 KIS를 호출하게 된다. 지수는 2건뿐이지만 요청량에 비례해 호출이 늘어난다.
 */
@Component
public class MarketIndexCache {

    /***
     * 보관 기간. 장중 폴백(실시간이 끊긴 상황)에서도 1분이면 화면이 멈춘 것처럼 보이지 않고,
     * 장외에는 값이 변하지 않으므로 충분하다.
     */
    static final Duration TTL = Duration.ofMinutes(1);

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public MarketIndexCache() {
        this(Clock.systemDefaultZone());
    }

    // TTL 경계를 테스트에서 시간 대기 없이 검증하기 위한 생성자
    MarketIndexCache(Clock clock) {
        this.clock = clock;
    }

    public Optional<IndexPriceRes.Info> find(String indexCode) {
        return Optional.ofNullable(entries.get(indexCode))
                .filter(this::isAlive)
                .map(Entry::info);
    }

    public void put(String indexCode, IndexPriceRes.Info info) {
        entries.put(indexCode, new Entry(info, clock.instant()));
    }

    private boolean isAlive(Entry entry) {
        return !entry.cachedAt().isBefore(clock.instant().minus(TTL));
    }

    private record Entry(IndexPriceRes.Info info, Instant cachedAt) {}
}
