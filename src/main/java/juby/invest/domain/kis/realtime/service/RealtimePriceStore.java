package juby.invest.domain.kis.realtime.service;

import juby.invest.domain.kis.realtime.dto.RealtimePrice;
import juby.invest.domain.kis.realtime.dto.RealtimeTick;
import org.springframework.stereotype.Component;

import java.time.Clock;

/***
 * 종목별 최신 실시간 체결가 저장소.
 * <p>
 * 세션별 담당 종목이 배타적이므로 키 경합이 없다. 조회/신선도 판정은 {@link RealtimeSnapshotStore}에 있다.
 */
@Component
public class RealtimePriceStore extends RealtimeSnapshotStore<RealtimePrice> {

    public RealtimePriceStore() {
        this(Clock.systemDefaultZone());
    }

    // 신선도 경계를 테스트에서 시간 대기 없이 검증하기 위한 생성자
    RealtimePriceStore(Clock clock) {
        super(clock);
    }

    /*** 수신한 체결로 해당 종목의 최신 시세를 덮어쓴다. */
    public void put(RealtimeTick tick) {
        save(tick.stockCode(), new RealtimePrice(tick, now()));
    }
}
