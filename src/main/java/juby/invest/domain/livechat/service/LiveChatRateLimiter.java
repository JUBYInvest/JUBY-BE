package juby.invest.domain.livechat.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/***
 * 회원별 채팅 전송 간격 제한.
 * <p>
 * 단일 인스턴스로만 운영하므로(CLAUDE.md의 실시간 시세 제약과 같음) 메모리에 둔다.
 * 인스턴스를 늘리면 회원이 인스턴스마다 따로 계산되므로 공유 저장소로 옮겨야 한다.
 * 맵은 채팅을 보낸 적 있는 회원 수만큼만 커지므로 따로 비우지 않는다.
 */
@Component
public class LiveChatRateLimiter {

    static final Duration MIN_INTERVAL = Duration.ofSeconds(1);

    private final Clock clock;
    private final ConcurrentHashMap<Long, Long> lastAcceptedAt = new ConcurrentHashMap<>();

    public LiveChatRateLimiter() {
        this(Clock.systemUTC());
    }

    LiveChatRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /***
     * 함수 기능: 마지막으로 허용한 전송에서 MIN_INTERVAL이 지났으면 이번 전송을 허용하고 기록한다.
     *          확인과 기록을 compute 하나로 묶어, 같은 회원의 동시 전송이 둘 다 통과하지 않게 한다.
     * @param memberId 보내는 회원 ID
     * @return 보내도 되면 true
     */
    public boolean tryAcquire(Long memberId) {
        long now = clock.millis();
        AtomicBoolean accepted = new AtomicBoolean(false);

        lastAcceptedAt.compute(memberId, (id, last) -> {
            if (last == null || now - last >= MIN_INTERVAL.toMillis()) {
                accepted.set(true);
                return now;
            }
            return last;
        });

        return accepted.get();
    }
}
