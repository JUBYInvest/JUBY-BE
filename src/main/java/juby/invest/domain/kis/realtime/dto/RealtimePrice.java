package juby.invest.domain.kis.realtime.dto;

import java.time.Instant;

/***
 * 저장소에 보관하는 실시간 시세. 체결 1건 + 수신 시각이다.
 * <p>
 * 신선도를 KIS의 체결시간이 아니라 <b>수신 시각</b>으로 판단하기 위해 Instant를 함께 들고 있다.
 * 체결시간은 HHmmss뿐이어서 날짜가 없고, 그대로 쓰면 전일 장 마감 값을 당일 값으로 오인한다.
 *
 * @param tick        체결 데이터
 * @param receivedAt  서버가 이 체결을 받은 시각
 */
public record RealtimePrice(
        RealtimeTick tick,
        Instant receivedAt
) implements RealtimeSnapshot {}
