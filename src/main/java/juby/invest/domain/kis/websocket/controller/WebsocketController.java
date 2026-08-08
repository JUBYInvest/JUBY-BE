package juby.invest.domain.kis.websocket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.domain.kis.websocket.dto.ApprovalKeyDto;
import juby.invest.domain.kis.websocket.exception.code.WebsocketSuccessCode;
import juby.invest.domain.kis.websocket.service.WebsocketService;
import juby.invest.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/websocket")
@Tag(name = "KIS 웹소켓 API", description = "실시간 접속키 발급 / 웹소켓 커넥션 수립 / 커넥션 종료 / 종목 실시간 체결가 구독 등록&해제")
public class WebsocketController {

    private final WebsocketService websocketService;

    /***
     * 함수 기능: KIS 실시간 접속키 발급 (확인용)
     * @return KIS 실시간 접속키
     */
    @PostMapping("/approval-key")
    @Operation(description = "KIS 실시간 접속키를 발급받는다..")
    public ApiResponse<ApprovalKeyDto.ApprovalKeyRes> getApprovalKey(){
        return ApiResponse.onSuccess(WebsocketSuccessCode.APPROVAL_KEY_ISSUED, websocketService.getApprovalKey());
    }

    /***
     * 함수 기능:
     * @return
     */
    @PostMapping("/connection")
    @Operation(description = "KIS 웹소켓 커넥션을 수립한다.")
    public ApiResponse<Void> connect(){
        websocketService.connect();
        return ApiResponse.onSuccess(WebsocketSuccessCode.CONNECTED, null);
    }

    /***
     * 함수 기능:
     * @return
     */
    @DeleteMapping("/connection")
    @Operation(description = "KIS 웹소켓 커넥션을 종료한다.")
    public ApiResponse<Void> disconnect(){
        websocketService.disconnect();
        return ApiResponse.onSuccess(WebsocketSuccessCode.DISCONNECTED, null);
    }

    /***
     * 함수 기능:
     * @return
     */
    @PostMapping("/subscriptions/{stockCode}")
    @Operation(description = "종목 실시간 체결가 구독을 등록한다.")
    public ApiResponse<Void> subscribeRealtimePrice(
            @PathVariable String stockCode
    ){
        websocketService.subscribeRealtimePrice(stockCode);
        return ApiResponse.onSuccess(WebsocketSuccessCode.SUBSCRIBED, null);
    }

    /***
     * 함수 기능:
     * @return
     */
    @DeleteMapping("/subscriptions/{stockCode}")
    @Operation(description = "종목 실시간 체결가 구독을 해지한다.")
    public ApiResponse<Void> unsubscribeRealtimePrice(
            @PathVariable String stockCode
    ){
        websocketService.unsubscribeRealtimePrice(stockCode);
        return ApiResponse.onSuccess(WebsocketSuccessCode.UNSUBSCRIBED, null);
    }
}
