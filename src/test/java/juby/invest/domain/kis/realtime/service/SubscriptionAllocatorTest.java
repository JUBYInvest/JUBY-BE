package juby.invest.domain.kis.realtime.service;

import juby.invest.domain.kis.realtime.dto.RealtimeSubscription;
import juby.invest.domain.kis.realtime.exception.RealtimeException;
import juby.invest.domain.kis.realtime.exception.code.RealtimeErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("실시간 구독 종목 분배")
class SubscriptionAllocatorTest {

    private static final String STOCK_TR = "H0STCNT0";
    private static final String INDEX_TR = "H0UPCNT0";

    /*** 지수 구독 2건 (코스피, 코스닥) */
    private static final List<RealtimeSubscription> INDICES = List.of(
            new RealtimeSubscription(INDEX_TR, "0001"),
            new RealtimeSubscription(INDEX_TR, "1001"));

    /*** 종목 구독 100건 (001000 ~ 001099) */
    private static List<RealtimeSubscription> hundredStockCodes() {
        List<RealtimeSubscription> subscriptions = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            subscriptions.add(new RealtimeSubscription(STOCK_TR, String.format("%06d", 1000 + i)));
        }
        return subscriptions;
    }

    private static RealtimeSubscription stock(String stockCode) {
        return new RealtimeSubscription(STOCK_TR, stockCode);
    }

    @Test
    @DisplayName("100종목을 3세션에 34/33/33으로 나눈다")
    void splitsEvenlyWithRemainderToFrontSessions() {

        List<List<RealtimeSubscription>> allocation = SubscriptionAllocator.allocate(hundredStockCodes(), 3);

        assertThat(allocation).hasSize(3);
        assertThat(allocation).extracting(List::size).containsExactly(34, 33, 33);
    }

    @Test
    @DisplayName("모든 종목이 중복 없이 한 세션에만 배정된다")
    void allocatesEveryStockExactlyOnce() {

        List<RealtimeSubscription> codes = hundredStockCodes();

        List<RealtimeSubscription> allocated = SubscriptionAllocator.allocate(codes, 3).stream()
                .flatMap(List::stream)
                .toList();

        assertThat(allocated).hasSameSizeAs(codes);
        assertThat(allocated).doesNotHaveDuplicates();
        assertThat(allocated).containsExactlyInAnyOrderElementsOf(codes);
    }

    @Test
    @DisplayName("입력 순서가 바뀌어도 같은 분배 결과가 나온다")
    void isDeterministicRegardlessOfInputOrder() {

        List<RealtimeSubscription> codes = hundredStockCodes();
        List<RealtimeSubscription> shuffled = new ArrayList<>(codes);
        Collections.shuffle(shuffled);

        // 재기동이나 재접속 후에도 같은 세션이 같은 종목을 담당해야 로그 추적이 끊기지 않는다.
        assertThat(SubscriptionAllocator.allocate(shuffled, 3))
                .isEqualTo(SubscriptionAllocator.allocate(codes, 3));
    }

    @Test
    @DisplayName("세션 1개는 등록 상한(41)까지만 담당한다")
    void rejectsWhenStocksExceedSessionCapacity() {

        List<RealtimeSubscription> codes = hundredStockCodes().subList(0, SubscriptionAllocator.MAX_SUBSCRIPTION_PER_SESSION + 1);

        assertThatThrownBy(() -> SubscriptionAllocator.allocate(codes, 1))
                .isInstanceOf(RealtimeException.class)
                .hasFieldOrPropertyWithValue("errorCode", RealtimeErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("상한을 꽉 채운 종목 수는 허용한다")
    void allowsExactlyAtCapacity() {

        int capacity = SubscriptionAllocator.MAX_SUBSCRIPTION_PER_SESSION * 2;
        List<RealtimeSubscription> codes = hundredStockCodes().subList(0, capacity);

        assertThat(SubscriptionAllocator.allocate(codes, 2))
                .extracting(List::size)
                .containsExactly(41, 41);
    }

    @Test
    @DisplayName("세션이 없으면 자격증명 미설정으로 본다")
    void rejectsZeroSessions() {

        List<RealtimeSubscription> codes = hundredStockCodes();

        assertThatThrownBy(() -> SubscriptionAllocator.allocate(codes, 0))
                .isInstanceOf(RealtimeException.class)
                .hasFieldOrPropertyWithValue("errorCode", RealtimeErrorCode.NO_CREDENTIAL_CONFIGURED);
    }

    @Test
    @DisplayName("고정 배정한 지수는 지정한 세션에만 들어간다")
    void pinsSubscriptionsToGivenSession() {

        // 모의 세션이 지수 TR을 지원하지 않을 수 있으므로 지수는 실전 세션(여기서는 0번)에 몰아 둔다.
        List<List<RealtimeSubscription>> allocation =
                SubscriptionAllocator.allocate(hundredStockCodes(), INDICES, 0, 3);

        assertThat(allocation).extracting(List::size).containsExactly(36, 33, 33);
        assertThat(allocation.getFirst()).containsAll(INDICES);
        assertThat(allocation.get(1)).doesNotContainAnyElementsOf(INDICES);
        assertThat(allocation.get(2)).doesNotContainAnyElementsOf(INDICES);
    }

    @Test
    @DisplayName("고정 배정 세션이 0번이 아니어도 그 세션에 들어간다")
    void pinsToNonFirstSession() {

        List<List<RealtimeSubscription>> allocation =
                SubscriptionAllocator.allocate(hundredStockCodes(), INDICES, 2, 3);

        assertThat(allocation).extracting(List::size).containsExactly(34, 33, 35);
        assertThat(allocation.get(2)).containsAll(INDICES);
    }

    @Test
    @DisplayName("상한 검사는 고정분을 더한 최종 크기로 한다")
    void checksLimitAfterPinning() {

        // 종목만 보면 41건으로 상한에 딱 맞지만, 지수 2건을 더하면 43건이 되어 넘는다.
        List<RealtimeSubscription> codes =
                hundredStockCodes().subList(0, SubscriptionAllocator.MAX_SUBSCRIPTION_PER_SESSION);

        assertThatThrownBy(() -> SubscriptionAllocator.allocate(codes, INDICES, 0, 1))
                .isInstanceOf(RealtimeException.class)
                .hasFieldOrPropertyWithValue("errorCode", RealtimeErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("고정 배정 세션 인덱스가 범위를 벗어나면 거부한다")
    void rejectsOutOfRangePinnedIndex() {

        assertThatThrownBy(() -> SubscriptionAllocator.allocate(hundredStockCodes(), INDICES, 3, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("종목 수가 세션 수보다 적으면 빈 담당 세션이 생긴다")
    void allowsEmptySessionWhenStocksAreFewerThanSessions() {

        List<List<RealtimeSubscription>> allocation =
                SubscriptionAllocator.allocate(List.of(stock("005930"), stock("035720")), 3);

        assertThat(allocation).extracting(List::size).containsExactly(1, 1, 0);
    }
}
