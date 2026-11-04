package juby.invest.domain.kis.realtime.service;

import juby.invest.domain.kis.realtime.dto.RealtimeSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/***
 * 키별 최신 실시간 스냅샷을 들고 있는 메모리 저장소의 공통 구현. (종목 시세, 지수)
 * <p>
 * 실시간 데이터는 DB에 적재하지 않는다. 장중 수신량은 하루 수백만 건이고, 화면이 필요한 것은 "지금 값" 하나다.
 * 그래서 키별 최신 1건만 덮어쓴다. 키 개수가 고정(종목 100 + 지수 2)이라 메모리는 늘지 않는다.
 * <p>
 * 쓰기는 웹소켓 수신 스레드(세션 3개), 읽기는 HTTP 요청 스레드에서 일어난다.
 * ConcurrentHashMap + 불변 record 조합이라 잠금 없이 안전하다.
 *
 * @param <T> 보관하는 스냅샷 타입
 */
abstract class RealtimeSnapshotStore<T extends RealtimeSnapshot> {

    /***
     * 이 시간이 지난 값은 없는 것으로 본다.
     * <p>
     * 신선도 판정이 필요한 이유는 장 종료 후 남은 값이 다음 거래일 아침까지 "실시간"으로 응답되는 것을 막는 것이다.
     * 30분으로 둔 이유: 장 종료(15:30) 기준 16:00까지 당일 마지막 값이 유지되는데,
     * 그 시각에 DailyPriceScheduler가 당일 일봉을 적재하므로 실시간 -> 일봉 전환이 끊김 없이 이어진다.
     * 더 짧게 잡으면 15:40~16:00 사이에 화면이 전일 종가로 되돌아간다.
     */
    static final Duration STALE_AFTER = Duration.ofMinutes(30);

    private final Map<String, T> snapshots = new ConcurrentHashMap<>();
    private final Clock clock;

    protected RealtimeSnapshotStore(Clock clock) {
        this.clock = clock;
    }

    /*** 현재 시각. 하위 클래스가 스냅샷을 만들 때 수신 시각으로 쓴다. */
    protected Instant now() {
        return clock.instant();
    }

    protected void save(String key, T snapshot) {
        snapshots.put(key, snapshot);
    }

    /***
     * 키 1건의 최신 값을 반환한다. 값이 없거나 오래됐으면 비어 있다.
     * 호출하는 쪽이 같은 신선도 판정을 중복 구현하지 않도록 여기서 걸러낸다.
     */
    public Optional<T> findFresh(String key) {
        return Optional.ofNullable(snapshots.get(key))
                .filter(this::isFresh);
    }

    /***
     * 신선한 값 전체를 스냅샷으로 반환한다. 목록 화면이 여러 건을 한 번에 조회할 때 쓴다.
     */
    public Map<String, T> findAllFresh() {

        Map<String, T> fresh = new HashMap<>();
        snapshots.forEach((key, snapshot) -> {
            if (isFresh(snapshot)){
                fresh.put(key, snapshot);
            }
        });
        return fresh;
    }

    private boolean isFresh(T snapshot) {

        Instant staleBefore = clock.instant().minus(STALE_AFTER);

        // 경계값(정확히 STALE_AFTER 경과)은 아직 신선한 것으로 본다.
        return !snapshot.receivedAt().isBefore(staleBefore);
    }
}
