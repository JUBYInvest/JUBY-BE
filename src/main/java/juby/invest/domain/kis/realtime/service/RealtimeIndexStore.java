package juby.invest.domain.kis.realtime.service;

import juby.invest.domain.kis.realtime.dto.RealtimeIndex;
import juby.invest.domain.kis.realtime.dto.RealtimeIndexTick;
import org.springframework.stereotype.Component;

import java.time.Clock;

/***
 * 업종코드별 최신 실시간 지수 저장소. (코스피 0001, 코스닥 1001)
 * <p>
 * 조회/신선도 판정은 {@link RealtimeSnapshotStore}에 있다. 지수 실시간이 끊기면 값이 오래되어
 * 자동으로 비워지고, 조회 쪽은 REST 폴백으로 내려간다.
 */
@Component
public class RealtimeIndexStore extends RealtimeSnapshotStore<RealtimeIndex> {

    public RealtimeIndexStore() {
        this(Clock.systemDefaultZone());
    }

    // 신선도 경계를 테스트에서 시간 대기 없이 검증하기 위한 생성자
    RealtimeIndexStore(Clock clock) {
        super(clock);
    }

    /*** 수신한 지수로 해당 업종의 최신 값을 덮어쓴다. */
    public void put(RealtimeIndexTick tick) {
        save(tick.indexCode(), new RealtimeIndex(tick, now()));
    }
}
