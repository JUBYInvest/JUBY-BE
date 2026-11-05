package juby.invest.domain.kis.realtime.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DisplayName("실시간 스트림 연결 저장소")
class RealtimeStreamRegistryTest {

    private final RealtimeStreamRegistry registry = new RealtimeStreamRegistry();

    private static SseEmitter emitter() {
        return mock(SseEmitter.class);
    }

    @Test
    @DisplayName("등록한 목록 화면 연결 전체에 이벤트를 보낸다")
    void broadcastsToAllEmitters() throws Exception {

        SseEmitter first = emitter();
        SseEmitter second = emitter();
        registry.register(first);
        registry.register(second);

        registry.broadcast("prices", "데이터");

        then(first).should().send(any(SseEmitter.SseEventBuilder.class));
        then(second).should().send(any(SseEmitter.SseEventBuilder.class));
        assertThat(registry.connectionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("전송에 실패한 연결은 걷어내고 다음 전송에서 제외한다")
    void discardsEmitterOnSendFailure() throws Exception {

        SseEmitter broken = emitter();
        SseEmitter alive = emitter();
        // 브라우저가 탭을 닫으면 전송 시점에야 알 수 있다.
        willThrow(new IOException("연결 끊김")).given(broken).send(any(SseEmitter.SseEventBuilder.class));

        registry.register(broken);
        registry.register(alive);

        registry.broadcast("prices", "데이터");
        registry.broadcast("prices", "데이터");

        // 끊긴 연결은 첫 시도에서만 호출되고 이후 목록에서 빠진다.
        then(broken).should(times(1)).send(any(SseEmitter.SseEventBuilder.class));
        then(alive).should(times(2)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(registry.connectionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("연결이 끝나면 콜백으로 스스로 빠진다")
    void removesEmitterOnCompletion() {

        SseEmitter emitter = emitter();
        registry.register(emitter);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        then(emitter).should().onCompletion(captor.capture());
        captor.getValue().run();

        assertThat(registry.connectionCount()).isZero();
    }

    @Test
    @DisplayName("타임아웃된 연결도 콜백으로 빠진다")
    void removesEmitterOnTimeout() {

        SseEmitter emitter = emitter();
        registry.register(emitter);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        then(emitter).should().onTimeout(captor.capture());
        captor.getValue().run();

        assertThat(registry.connectionCount()).isZero();
    }

    @Test
    @DisplayName("종목 1건 연결은 해당 종목의 이벤트만 받는다")
    void routesStockEventToMatchingEmittersOnly() throws Exception {

        SseEmitter samsung = emitter();
        SseEmitter kakao = emitter();
        registry.register("005930", samsung);
        registry.register("035720", kakao);

        registry.sendToStock("005930", "price", "데이터");

        then(samsung).should().send(any(SseEmitter.SseEventBuilder.class));
        then(kakao).should(never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("목록 화면 연결은 종목 1건 이벤트를 받지 않는다")
    void doesNotLeakStockEventToListEmitters() throws Exception {

        SseEmitter list = emitter();
        registry.register(list);

        registry.sendToStock("005930", "price", "데이터");

        then(list).should(never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("구독자가 없는 종목에 보내도 아무 일이 없다")
    void ignoresSendToStockWithoutSubscribers() {

        org.assertj.core.api.Assertions
                .assertThatCode(() -> registry.sendToStock("005930", "price", "데이터"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("하트비트는 두 종류의 연결 모두에 보낸다")
    void sendsHeartbeatToEveryConnection() throws Exception {

        SseEmitter list = emitter();
        SseEmitter stock = emitter();
        registry.register(list);
        registry.register("005930", stock);

        registry.heartbeat();

        then(list).should().send(any(SseEmitter.SseEventBuilder.class));
        then(stock).should().send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("하트비트 전송에 실패한 연결도 걷어낸다")
    void discardsEmitterOnHeartbeatFailure() throws Exception {

        SseEmitter broken = emitter();
        willThrow(new IOException("연결 끊김")).given(broken).send(any(SseEmitter.SseEventBuilder.class));
        registry.register(broken);

        registry.heartbeat();

        assertThat(registry.connectionCount()).isZero();
    }

    @Test
    @DisplayName("한 연결에만 보내는 전송은 성공 여부를 알려준다")
    void reportsSingleSendResult() throws Exception {

        SseEmitter alive = emitter();
        SseEmitter broken = emitter();
        willThrow(new IOException("연결 끊김")).given(broken).send(any(SseEmitter.SseEventBuilder.class));

        assertThat(registry.sendTo(alive, "prices", "데이터")).isTrue();
        assertThat(registry.sendTo(broken, "prices", "데이터")).isFalse();
    }
}
