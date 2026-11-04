package juby.invest.domain.kis.realtime.dto;

/***
 * 실시간 구독 1건. 세션당 41건까지만 등록할 수 있으므로, 이 record 1개가 그 상한의 1칸을 쓴다.
 *
 * @param trId  구독할 TR (체결가 H0STCNT0, 지수 H0UPCNT0 ...)
 * @param trKey 구독 대상 키 (종목코드 6자리, 업종코드 4자리 ...)
 */
public record RealtimeSubscription(
        String trId,
        String trKey
) {}
