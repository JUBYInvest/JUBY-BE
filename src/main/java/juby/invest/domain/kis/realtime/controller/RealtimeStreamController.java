package juby.invest.domain.kis.realtime.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import juby.invest.domain.kis.realtime.service.RealtimeStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/***
 * 실시간 시세 SSE 스트림.
 * <p>
 * 다른 컨트롤러와 달리 ApiResponse 봉투를 쓰지 않는다. 응답이 한 번에 끝나는 JSON이 아니라
 * 이벤트가 계속 흐르는 스트림이라, 봉투를 씌울 자리가 없다. 이벤트별 payload가 곧 데이터다.
 * <p>
 * 초기 데이터는 기존 REST(/api/stocks, /api/market-indices)가 담당하고, 이 스트림은 그 이후의 갱신분을 민다.
 */
@Tag(name = "실시간 시세 스트림 API", description = "장중 시세 변경분을 SSE로 밀어 보낸다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stream")
public class RealtimeStreamController {

    /***
     * nginx가 응답을 버퍼링하면 이벤트가 쌓였다가 한꺼번에 나간다. 그러면 실시간이 아니다.
     * 이 헤더로 nginx 설정을 건드리지 않고 해당 응답만 버퍼링을 끈다.
     */
    private static final String NGINX_BUFFERING_HEADER = "X-Accel-Buffering";

    private final RealtimeStreamService realtimeStreamService;

    @Operation(summary = "전 종목 실시간 시세 스트림",
            description = "연결 직후 현재 스냅샷을 한 번 보내고, 이후 1초 주기로 변경된 종목만 보낸다. "
                    + "이벤트 이름은 종목이 prices, 지수가 indices다.")
    @GetMapping(value = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamPrices() {
        return stream(realtimeStreamService.openPriceStream());
    }

    @Operation(summary = "종목 1건 실시간 시세 스트림",
            description = "상세 화면용. 해당 종목의 체결만 보낸다. 이벤트 이름은 price다.")
    @GetMapping(value = "/prices/{stockCode}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamStockPrice(
            @PathVariable @Pattern(regexp = "[0-9]{6}", message = "종목 코드는 6자리 숫자여야 합니다.") String stockCode) {
        return stream(realtimeStreamService.openStockStream(stockCode));
    }

    private ResponseEntity<SseEmitter> stream(SseEmitter emitter) {
        return ResponseEntity.ok()
                .header(NGINX_BUFFERING_HEADER, "no")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(emitter);
    }
}
