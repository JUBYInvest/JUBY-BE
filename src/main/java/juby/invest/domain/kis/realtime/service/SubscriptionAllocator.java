package juby.invest.domain.kis.realtime.service;

import juby.invest.domain.kis.realtime.dto.RealtimeSubscription;
import juby.invest.domain.kis.realtime.exception.RealtimeException;
import juby.invest.domain.kis.realtime.exception.code.RealtimeErrorCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/***
 * 구독 항목을 세션별로 나눈다.
 * <p>
 * KIS는 웹소켓 세션 1개당 실시간 등록을 41건까지만 받는다. 100종목을 모두 덮으려면 세션을 여러 개 띄워
 * 종목을 나눠 담당시켜야 한다.
 * <p>
 * 분배는 <b>종목코드 정렬 후 균등 분할</b>이다. 해시 분배와 달리 결정적이어서, 재기동이나 재접속 후에도
 * 같은 세션이 같은 종목을 담당한다. 로그에서 "이 종목은 어느 세션 담당인지"를 추적할 수 있어야 장애를 좁힐 수 있다.
 */
public class SubscriptionAllocator {

    /*** 세션 1개가 등록할 수 있는 실시간 구독 수 상한 (KIS 제한) */
    public static final int MAX_SUBSCRIPTION_PER_SESSION = 41;

    /*** 구독 정렬 기준: TR -> 키. 입력 순서가 달라도 같은 분배 결과가 나오게 한다. */
    private static final Comparator<RealtimeSubscription> DETERMINISTIC_ORDER =
            Comparator.comparing(RealtimeSubscription::trId).thenComparing(RealtimeSubscription::trKey);

    /*** 구독 항목을 세션 수만큼 균등 분할한다. (고정 배정 없음) */
    public static List<List<RealtimeSubscription>> allocate(List<RealtimeSubscription> subscriptions, int sessionCount) {
        return allocate(subscriptions, List.of(), 0, sessionCount);
    }

    /***
     * 구독 항목을 분배한다. 일부 항목은 특정 세션에 고정할 수 있다.
     * <p>
     * 고정 배정이 필요한 이유: 모의투자 세션이 지원하지 않을 수 있는 TR(지수 등)을 모의 세션에 등록하면
     * 거부도 없이 조용히 데이터가 오지 않을 수 있다. 그런 항목은 실전 세션에 몰아 둔다.
     *
     * @param evenly             세션에 균등 분할할 항목 (종목)
     * @param pinned             특정 세션에 고정할 항목 (지수)
     * @param pinnedSessionIndex 고정 항목을 받을 세션 인덱스
     * @param sessionCount       띄울 세션 수
     * @return 세션별 담당 구독 목록. 인덱스가 세션 순서와 일치한다.
     */
    public static List<List<RealtimeSubscription>> allocate(
            List<RealtimeSubscription> evenly,
            List<RealtimeSubscription> pinned,
            int pinnedSessionIndex,
            int sessionCount) {

        if (sessionCount <= 0){
            throw new RealtimeException(RealtimeErrorCode.NO_CREDENTIAL_CONFIGURED);
        }

        if (!pinned.isEmpty() && (pinnedSessionIndex < 0 || pinnedSessionIndex >= sessionCount)){
            throw new IllegalArgumentException(
                    "고정 배정할 세션 인덱스가 범위를 벗어났습니다. 인덱스: " + pinnedSessionIndex + ", 세션 수: " + sessionCount);
        }

        List<RealtimeSubscription> sorted = sortDeterministically(evenly);

        int base = sorted.size() / sessionCount;
        int remainder = sorted.size() % sessionCount;

        List<List<RealtimeSubscription>> allocation = new ArrayList<>(sessionCount);
        int from = 0;
        for (int i = 0; i < sessionCount; i++) {
            // 나머지는 앞쪽 세션부터 한 개씩 더 받는다. (100종목 / 3세션 -> 34, 33, 33)
            int size = base + (i < remainder ? 1 : 0);
            allocation.add(new ArrayList<>(sorted.subList(from, from + size)));
            from += size;
        }

        allocation.get(pinnedSessionIndex).addAll(sortDeterministically(pinned));

        // 상한 검사는 고정분을 더한 최종 크기로 한다.
        // 조용히 넘기면 상한을 넘긴 항목이 영구히 구독되지 않은 채 운영된다.
        for (List<RealtimeSubscription> sessionSubscriptions : allocation) {
            if (sessionSubscriptions.size() > MAX_SUBSCRIPTION_PER_SESSION){
                throw new RealtimeException(RealtimeErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED);
            }
        }

        return allocation.stream()
                .map(List::copyOf)
                .toList();
    }

    private static List<RealtimeSubscription> sortDeterministically(List<RealtimeSubscription> subscriptions) {
        return subscriptions.stream()
                .distinct()
                .sorted(DETERMINISTIC_ORDER)
                .toList();
    }
}
