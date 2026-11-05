package juby.invest.domain.kis.realtime.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/***
 * 열려 있는 SSE 연결을 보관하고, 끊긴 연결을 걷어낸다.
 * <p>
 * 연결은 두 종류다. 목록 화면은 전 종목을 받고(allEmitters), 상세 화면은 종목 1건만 받는다(stockEmitters).
 * <p>
 * 브로드캐스트는 스케줄러 스레드에서, 등록/해제는 요청 스레드와 컨테이너 콜백에서 일어난다.
 * 순회 중 변경이 잦으므로 CopyOnWriteArrayList를 쓴다(연결 수는 많아야 수백이고 변경보다 순회가 압도적으로 잦다).
 */
@Slf4j
@Component
public class RealtimeStreamRegistry {

    /*** 목록 화면 연결 */
    private final List<SseEmitter> allEmitters = new CopyOnWriteArrayList<>();

    /*** 종목코드 -> 상세 화면 연결 */
    private final Map<String, List<SseEmitter>> stockEmitters = new ConcurrentHashMap<>();

    /***
     * 목록 화면 연결을 등록한다. 끊김/타임아웃/오류 시 스스로 빠지도록 콜백을 건다.
     */
    public void register(SseEmitter emitter) {
        allEmitters.add(emitter);
        removeOnClose(emitter, () -> allEmitters.remove(emitter));
    }

    /***
     * 상세 화면 연결을 등록한다.
     */
    public void register(String stockCode, SseEmitter emitter) {

        List<SseEmitter> emitters = stockEmitters.computeIfAbsent(stockCode, code -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        // 빈 리스트는 남겨 둔다. 종목 수가 100개로 고정이라 누수가 아니고,
        // 지우려면 등록과 경합해 방금 등록한 연결을 잃을 수 있다.
        removeOnClose(emitter, () -> emitters.remove(emitter));
    }

    private void removeOnClose(SseEmitter emitter, Runnable removal) {
        emitter.onCompletion(removal);
        emitter.onTimeout(removal);
        emitter.onError(error -> removal.run());
    }

    /***
     * 목록 화면 연결 전체에 이벤트를 보낸다.
     *
     * @param eventName SSE 이벤트 이름. 프론트가 addEventListener로 구분한다.
     */
    public void broadcast(String eventName, Object data) {
        allEmitters.forEach(emitter -> send(emitter, eventName, data, allEmitters));
    }

    /***
     * 해당 종목의 상세 화면 연결에만 이벤트를 보낸다.
     */
    public void sendToStock(String stockCode, String eventName, Object data) {

        List<SseEmitter> emitters = stockEmitters.get(stockCode);
        if (emitters == null || emitters.isEmpty()){
            return;
        }
        emitters.forEach(emitter -> send(emitter, eventName, data, emitters));
    }

    /***
     * 한 연결에만 보낸다. 연결 직후 현재 스냅샷을 내려줄 때 쓴다.
     *
     * @return 전송에 성공했으면 true
     */
    public boolean sendTo(SseEmitter emitter, String eventName, Object data) {
        return send(emitter, eventName, data, null);
    }

    /***
     * 연결 유지용 주석을 보낸다.
     * <p>
     * 장외 시간이나 체결이 뜸한 구간에는 보낼 데이터가 없는데, 그대로 두면 프록시의 idle 타임아웃에 끊긴다.
     * SSE 주석은 클라이언트에서 이벤트로 잡히지 않으므로 프론트 코드에 영향이 없다.
     */
    public void heartbeat() {

        allEmitters.forEach(emitter -> sendComment(emitter, allEmitters));
        stockEmitters.values().forEach(emitters ->
                emitters.forEach(emitter -> sendComment(emitter, emitters)));
    }

    public int connectionCount() {
        return allEmitters.size() + stockEmitters.values().stream().mapToInt(List::size).sum();
    }

    private boolean send(SseEmitter emitter, String eventName, Object data, List<SseEmitter> owner) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;

        } catch (Exception e) {
            // 브라우저가 탭을 닫으면 전송 시점에야 알 수 있다. 장애가 아니므로 조용히 걷어낸다.
            discard(emitter, owner);
            return false;
        }
    }

    private void sendComment(SseEmitter emitter, List<SseEmitter> owner) {
        try {
            emitter.send(SseEmitter.event().comment("keep-alive"));
        } catch (Exception e) {
            discard(emitter, owner);
        }
    }

    private void discard(SseEmitter emitter, List<SseEmitter> owner) {

        if (owner != null){
            owner.remove(emitter);
        }

        // completeWithError는 등록해 둔 onError 콜백을 불러 중복 제거를 시도할 수 있지만,
        // List.remove는 멱등이라 문제가 없다. 컨테이너가 응답을 정리하게 하려면 호출해야 한다.
        try {
            emitter.completeWithError(new IllegalStateException("SSE 전송 실패로 연결을 정리합니다."));
        } catch (Exception ignored) {
            // 이미 완료된 연결이면 여기서 또 예외가 난다. 정리가 목적이므로 무시한다.
        }
    }
}
