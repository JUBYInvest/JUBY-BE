package juby.invest.domain.kis.realtime.dto;

import java.time.Instant;

/***
 * 저장소에 보관하는 실시간 지수. 지수 수신 1건 + 수신 시각이다.
 *
 * @param tick        지수 데이터
 * @param receivedAt  서버가 이 값을 받은 시각
 */
public record RealtimeIndex(
        RealtimeIndexTick tick,
        Instant receivedAt
) implements RealtimeSnapshot {}
