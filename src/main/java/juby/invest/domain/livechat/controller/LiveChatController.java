package juby.invest.domain.livechat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import juby.invest.domain.livechat.dto.LiveChatResDto;
import juby.invest.domain.livechat.exception.code.LiveChatSuccessCode;
import juby.invest.domain.livechat.service.LiveChatService;
import juby.invest.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/live-chat")
@Tag(name = "실시간 채팅 API", description = "홈 화면 실시간 채팅의 이전 대화를 조회한다. 비로그인도 조회할 수 있다. " +
        "메시지 전송과 수신은 STOMP(/ws/live-chat)로 한다.")
public class LiveChatController {

    private final LiveChatService liveChatService;

    @Operation(summary = "이전 대화 조회",
            description = "가장 최근 대화부터 size개를 오래된 순으로 반환한다(화면 위 -> 아래 순서). " +
                    "더 이전 대화는 응답의 nextCursor를 cursor로 보내 조회한다. 서버는 최근 300개만 보관한다.")
    @GetMapping("/messages")
    public ApiResponse<LiveChatResDto.MessageList> getMessages(
            @Parameter(description = "이전 응답의 nextCursor (가장 최근 대화면 생략)")
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") @Min(value = 1, message = "size는 1 이상이어야 합니다.") @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    ) {
        return ApiResponse.onSuccess(LiveChatSuccessCode.MESSAGE_LIST_OK, liveChatService.getMessages(cursor, size));
    }
}
