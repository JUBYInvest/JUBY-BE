package juby.invest.domain.livechat.controller;

import juby.invest.domain.auth.exception.AuthException;
import juby.invest.domain.auth.exception.code.AuthErrorCode;
import juby.invest.domain.auth.service.AuthService;
import juby.invest.domain.livechat.config.LiveChatDestinations;
import juby.invest.domain.livechat.config.LiveChatPrincipal;
import juby.invest.domain.livechat.dto.LiveChatReqDto;
import juby.invest.domain.livechat.dto.LiveChatResDto;
import juby.invest.domain.livechat.exception.LiveChatException;
import juby.invest.domain.livechat.exception.code.LiveChatErrorCode;
import juby.invest.domain.livechat.service.LiveChatService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.code.GeneralErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

/***
 * 실시간 채팅 메시지 전송 (STOMP).
 * <p>
 * 클라이언트는 /app/live-chat/messages로 {"content": "..."}를 SEND하고, /topic/live-chat을 구독해 모든 메시지를 받는다.
 * 실패하면 보낸 세션에만 /user/queue/errors로 ApiResponse 모양의 실패 응답이 간다. 연결은 끊지 않는다.
 * <p>
 * 올라온 메시지는 수정/삭제할 수 없으므로 전송 외의 핸들러는 없다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LiveChatMessageController {

    private final LiveChatService liveChatService;
    private final AuthService authService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping(LiveChatDestinations.SEND_MAPPING)
    public void send(@Payload LiveChatReqDto.SendReq req, Principal principal) {

        Long memberId = requireMember(principal);

        // send()의 트랜잭션이 커밋된 뒤에 뿌린다. 저장에 실패한 메시지가 화면에만 보였다가 새로고침하면 사라지는 일이 없다.
        LiveChatResDto.Message message = liveChatService.send(memberId, req == null ? null : req.content());
        messagingTemplate.convertAndSend(LiveChatDestinations.TOPIC, message);
    }

    /***
     * 함수 기능: 보낸 사람이 지금 채팅할 수 있는 회원인지 확인한다.
     *          연결은 CONNECT 때 한 번만 인증하므로, 연결이 AT 만료(30분)나 로그아웃보다 오래 살 수 있다.
     *          그래서 보낼 때마다 만료와 블랙리스트를 다시 본다. 만료면 프론트는 재발급 후 다시 연결한다.
     */
    private Long requireMember(Principal principal) {

        if (!(principal instanceof LiveChatPrincipal user) || user.isGuest()) {
            throw new LiveChatException(LiveChatErrorCode.LOGIN_REQUIRED);
        }
        if (user.isExpired(LocalDateTime.now())) {
            throw new AuthException(AuthErrorCode.EXPIRED_TOKEN);
        }
        if (authService.isBlacklisted(user.jti())) {
            throw new AuthException(AuthErrorCode.TOKEN_BLACKLISTED);
        }
        return user.memberId();
    }

    @MessageExceptionHandler(ProjectException.class)
    @SendToUser(destinations = LiveChatDestinations.ERRORS, broadcast = false)
    public ApiResponse<Void> handleProjectException(ProjectException e, Principal principal) {
        BaseErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus().is4xxClientError()) {
            log.warn("채팅 전송 거부: code={}, user={}", errorCode.getCode(), nameOf(principal));
        } else {
            log.error("채팅 전송 실패: code={}, user={}", errorCode.getCode(), nameOf(principal), e);
        }
        return ApiResponse.onFailure(errorCode, null);
    }

    // JSON이 아니거나 모양이 다른 본문
    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser(destinations = LiveChatDestinations.ERRORS, broadcast = false)
    public ApiResponse<Void> handleConversionException(MessageConversionException e, Principal principal) {
        log.warn("채팅 메시지 변환 실패: user={}", nameOf(principal));
        return ApiResponse.onFailure(LiveChatErrorCode.INVALID_MESSAGE, null);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = LiveChatDestinations.ERRORS, broadcast = false)
    public ApiResponse<Void> handleException(Exception e, Principal principal) {
        log.error("채팅 전송 중 예상하지 못한 오류가 발생했습니다. user={}", nameOf(principal), e);
        return ApiResponse.onFailure(GeneralErrorCode.INTERNAL_SERVER_ERROR, null);
    }

    // 메시지 본문은 남기지 않는다. 회원이면 회원 ID, 비로그인이면 guest-{uuid}다.
    private String nameOf(Principal principal) {
        return principal == null ? "unknown" : principal.getName();
    }
}
