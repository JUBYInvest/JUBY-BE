package juby.invest.domain.kis.realtime.controller;

import juby.invest.domain.kis.realtime.service.RealtimeStreamService;
import juby.invest.domain.stock.exception.StockException;
import juby.invest.domain.stock.exception.code.StockErrorCode;
import juby.invest.global.apiPayload.handler.GeneralExceptionAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스트림 엔드포인트 검증.
 * <p>
 * 정상 연결은 <b>컨트롤러 메서드를 직접 호출해</b> 확인한다. MockMvc로 열면 비동기 요청이라
 * 응답 헤더가 아직 기록되지 않아(디스패치 전) 검증할 수 없고, 열린 채로 남은 연결이
 * 테스트 실행을 몇십 분씩 붙잡는다. 검증 대상은 상태·헤더·위임뿐이라 직접 호출로 충분하다.
 * <p>
 * 요청 매핑과 파라미터 검증은 연결을 만들지 않는 경로(400/404)에서 MockMvc로 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("실시간 시세 스트림 엔드포인트")
class RealtimeStreamControllerTest {

    @Mock private RealtimeStreamService realtimeStreamService;

    private RealtimeStreamController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new RealtimeStreamController(realtimeStreamService);

        // 전체 컨텍스트와 시큐리티 없이 컨트롤러 + 예외 어드바이스만 올린다.
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("목록 스트림은 200과 함께 열린 연결을 돌려준다")
    void opensPriceStream() {

        SseEmitter emitter = new SseEmitter();
        given(realtimeStreamService.openPriceStream()).willReturn(emitter);

        ResponseEntity<SseEmitter> response = controller.streamPrices();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(emitter);
    }

    @Test
    @DisplayName("프록시 버퍼링을 끄는 헤더를 함께 내린다")
    void disablesProxyBuffering() {

        given(realtimeStreamService.openPriceStream()).willReturn(new SseEmitter());

        // 이 헤더가 없으면 nginx가 이벤트를 모아 두었다가 한꺼번에 내보낸다. 그러면 실시간이 아니다.
        ResponseEntity<SseEmitter> response = controller.streamPrices();

        assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-cache");
    }

    @Test
    @DisplayName("종목 1건 스트림은 종목코드를 서비스로 넘긴다")
    void opensStockStream() {

        SseEmitter emitter = new SseEmitter();
        given(realtimeStreamService.openStockStream("005930")).willReturn(emitter);

        ResponseEntity<SseEmitter> response = controller.streamStockPrice("005930");

        assertThat(response.getBody()).isSameAs(emitter);
        assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
        then(realtimeStreamService).should().openStockStream("005930");
    }

    @Test
    @DisplayName("종목코드 형식이 6자리 숫자가 아니면 거부한다")
    void rejectsMalformedStockCode() throws Exception {

        mockMvc.perform(get("/api/stream/prices/12345"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/stream/prices/abcdef"))
                .andExpect(status().isBadRequest());

        // 형식에서 걸러졌으므로 서비스까지 내려가지 않는다.
        then(realtimeStreamService).should(org.mockito.Mockito.never()).openStockStream(anyString());
    }

    @Test
    @DisplayName("없는 종목이면 종목 조회 실패로 응답한다")
    void rejectsUnknownStock() throws Exception {

        willThrow(new StockException(StockErrorCode.STOCK_NOT_FOUND))
                .given(realtimeStreamService).openStockStream(anyString());

        mockMvc.perform(get("/api/stream/prices/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(StockErrorCode.STOCK_NOT_FOUND.getCode()));
    }
}
