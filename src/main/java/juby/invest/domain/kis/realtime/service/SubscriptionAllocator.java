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

    /***
     * 구독 항목을 세션 수만큼 균등 분할한다.
     *
     * @param subscriptions 구독할 항목
     * @param sessionCount  띄울 세션 수 (= 사용 가능한 자격증명 수)
     * @return 세션별 담당 구독 목록. 인덱스가 세션 순서와 일치한다.
     */
    public static List<List<RealtimeSubscription>> allocate(List<RealtimeSubscription> subscriptions, int sessionCount) {

        if (sessionCount <= 0){
            throw new RealtimeException(RealtimeErrorCode.NO_CREDENTIAL_CONFIGURED);
        }

        List<RealtimeSubscription> sorted = subscriptions.stream()
                .distinct()
                .sorted(DETERMINISTIC_ORDER)
                .toList();

        // 상한을 넘으면 접속 전에 터뜨린다. 조용히 넘기면 뒤쪽 항목이 영구히 구독되지 않은 채 운영된다.
        if (sorted.size() > (long) MAX_SUBSCRIPTION_PER_SESSION * sessionCount){
            throw new RealtimeException(RealtimeErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED);
        }

        int base = sorted.size() / sessionCount;
        int remainder = sorted.size() % sessionCount;

        List<List<RealtimeSubscription>> allocation = new ArrayList<>(sessionCount);
        int from = 0;
        for (int i = 0; i < sessionCount; i++) {
            // 나머지는 앞쪽 세션부터 한 개씩 더 받는다. (100종목 / 3세션 -> 34, 33, 33)
            int size = base + (i < remainder ? 1 : 0);
            allocation.add(List.copyOf(sorted.subList(from, from + size)));
            from += size;
        }
        return List.copyOf(allocation);
    }
}
