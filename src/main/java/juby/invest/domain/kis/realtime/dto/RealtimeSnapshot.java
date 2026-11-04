package juby.invest.domain.kis.realtime.dto;

import java.time.Instant;

/***
 * 수신 시각을 아는 실시간 스냅샷. 신선도 판정을 저장소가 공통으로 하기 위한 최소 계약이다.
 * <p>
 * 신선도를 KIS가 준 시각이 아니라 <b>수신 시각</b>으로 보는 이유: 체결시간/영업시간 필드는
 * HHmmss뿐이어서 날짜가 없고, 그대로 쓰면 전일 장 마감 값을 당일 값으로 오인한다.
 */
public interface RealtimeSnapshot {

    Instant receivedAt();
}
