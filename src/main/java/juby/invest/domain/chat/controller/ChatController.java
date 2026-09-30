package juby.invest.domain.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import juby.invest.domain.chat.dto.ChatReqDto;
import juby.invest.domain.chat.dto.ChatResDto;
import juby.invest.domain.chat.exception.code.ChatSuccessCode;
import juby.invest.domain.chat.service.ChatService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-sessions")
@Tag(name = "챗봇 대화방 API", description = "챗봇 대화방(세션) 생성/목록/상세 조회, 제목 변경, 삭제를 제공한다.")
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "대화방 생성", description = "빈 대화방을 새로 생성한다. 제목은 기본값('새 대화')이며 이후 제목 변경 API로 수정할 수 있다.")
    @PostMapping
    public ApiResponse<ChatResDto.SessionSummary> createSession(
            @AuthenticationPrincipal CustomOAuth2User user
    ) {
        return ApiResponse.onSuccess(ChatSuccessCode.SESSION_CREATE_OK, chatService.createSession(user.getId()));
    }

    @Operation(summary = "대화방 목록 조회", description = "로그인 사용자의 대화방 목록을 최근 대화 순으로 반환한다.")
    @GetMapping
    public ApiResponse<List<ChatResDto.SessionSummary>> getSessions(
            @AuthenticationPrincipal CustomOAuth2User user
    ) {
        return ApiResponse.onSuccess(ChatSuccessCode.SESSION_LIST_OK, chatService.getSessions(user.getId()));
    }

    @Operation(summary = "대화방 상세 조회", description = "대화방의 전체 메시지를 작성 순으로 반환한다. 본인 소유 대화방이 아니면 403이 반환된다.")
    @GetMapping("/{chatSessionId}")
    public ApiResponse<ChatResDto.SessionDetail> getSessionDetail(
            @AuthenticationPrincipal CustomOAuth2User user,
            @PathVariable Long chatSessionId
    ) {
        return ApiResponse.onSuccess(
                ChatSuccessCode.SESSION_DETAIL_OK, chatService.getSessionDetail(user.getId(), chatSessionId));
    }

    @Operation(summary = "대화방 제목 변경")
    @PatchMapping("/{chatSessionId}")
    public ApiResponse<ChatResDto.SessionSummary> updateTitle(
            @AuthenticationPrincipal CustomOAuth2User user,
            @PathVariable Long chatSessionId,
            @Valid @RequestBody ChatReqDto.UpdateTitleRequest dto
    ) {
        return ApiResponse.onSuccess(
                ChatSuccessCode.TITLE_UPDATE_OK, chatService.updateTitle(user.getId(), chatSessionId, dto.title()));
    }

    @Operation(summary = "대화방 삭제", description = "대화방과 하위 메시지를 모두 삭제한다.")
    @DeleteMapping("/{chatSessionId}")
    public ApiResponse<Void> deleteSession(
            @AuthenticationPrincipal CustomOAuth2User user,
            @PathVariable Long chatSessionId
    ) {
        chatService.deleteSession(user.getId(), chatSessionId);
        return ApiResponse.onSuccess(ChatSuccessCode.SESSION_DELETE_OK, null);
    }
}