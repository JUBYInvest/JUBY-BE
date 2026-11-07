package juby.invest.domain.livechat.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.code.GeneralErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/***
 * 인터셉터에서 거부된 프레임(무효 토큰으로 CONNECT, 허용되지 않은 목적지)을 STOMP ERROR 프레임으로 바꾼다.
 * <p>
 * 기본 핸들러는 예외 메시지를 그대로 내보내는데, ProjectException은 메시지가 없고 예상 못한 예외는 내부 정보가 샐 수 있다.
 * 그래서 HTTP 응답과 같은 ApiResponse 모양으로 맞춘다. ERROR 프레임의 message 헤더에는 에러 코드를 넣는다.
 * (예: AUTH401_1이면 프론트는 AT를 재발급받고 다시 연결한다)
 * <p>
 * 로그 레벨은 GeneralExceptionAdvice와 같은 규칙: 4xx는 WARN, 그 밖은 ERROR.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveChatStompErrorHandler extends StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {

        BaseErrorCode errorCode = resolve(ex);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(errorCode.getCode());
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);

        // 클라이언트가 receipt를 요청했으면 어느 프레임이 실패했는지 알 수 있게 돌려준다.
        if (clientMessage != null) {
            StompHeaderAccessor clientAccessor = StompHeaderAccessor.wrap(clientMessage);
            if (clientAccessor.getReceipt() != null) {
                accessor.setReceiptId(clientAccessor.getReceipt());
            }
        }

        return MessageBuilder.createMessage(toJson(errorCode), accessor.getMessageHeaders());
    }

    private BaseErrorCode resolve(Throwable ex) {

        // 인터셉터에서 던진 예외는 MessageDeliveryException 등으로 한 번 감싸져 온다.
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ProjectException projectException) {
                BaseErrorCode errorCode = projectException.getErrorCode();
                if (errorCode.getStatus().is4xxClientError()) {
                    log.warn("채팅 프레임 거부: {}", errorCode.getCode());
                } else {
                    log.error("채팅 프레임 처리 실패: {}", errorCode.getCode(), projectException);
                }
                return errorCode;
            }
        }

        log.error("채팅 프레임 처리 중 예상하지 못한 오류가 발생했습니다.", ex);
        return GeneralErrorCode.INTERNAL_SERVER_ERROR;
    }

    private byte[] toJson(BaseErrorCode errorCode) {
        try {
            return objectMapper.writeValueAsBytes(ApiResponse.onFailure(errorCode, null));
        } catch (JsonProcessingException e) {
            // 고정된 필드만 있는 객체라 실제로는 일어나지 않는다. 그래도 ERROR 프레임은 나가야 한다.
            return new byte[0];
        }
    }
}
