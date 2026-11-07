package juby.invest.domain.livechat.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import juby.invest.domain.auth.exception.AuthException;
import juby.invest.domain.auth.exception.code.AuthErrorCode;
import juby.invest.domain.auth.service.AuthService;
import juby.invest.domain.livechat.exception.LiveChatException;
import juby.invest.domain.livechat.exception.code.LiveChatErrorCode;
import juby.invest.global.security.entity.CustomOAuth2User;
import juby.invest.global.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/***
 * 클라이언트가 보낸 STOMP 프레임을 브로커/컨트롤러로 넘기기 전에 검사한다.
 * <p>
 * <b>왜 HTTP 필터가 아니라 여기서 인증하나</b>: 브라우저 WebSocket은 핸드셰이크에 Authorization 헤더를
 * 붙일 수 없다(SSE와 같은 제약). 그래서 /ws/**는 SecurityConfig에서 열어 두고, STOMP CONNECT 프레임의
 * Authorization 헤더로 인증한다.
 * <ul>
 *   <li>CONNECT: 토큰이 없으면 비로그인(읽기 전용) 세션, 있으면 검증해서 회원 세션. 토큰이 무효하면 연결을 거부한다.
 *       (조용히 비로그인으로 떨어뜨리면 로그인했는데 채팅이 안 되는 원인을 프론트가 알 수 없다)</li>
 *   <li>SUBSCRIBE/SEND: 정해진 목적지만 허용한다. 특히 SimpleBroker는 클라이언트가 /topic으로 직접 SEND하면
 *       그대로 모두에게 뿌리므로, 막지 않으면 로그인/닉네임/길이 검사와 저장을 전부 건너뛸 수 있다.</li>
 * </ul>
 * 회원 여부, 토큰 만료, 닉네임 검사는 SEND를 처리하는 컨트롤러에서 한다. 거기서 실패해야 연결을 끊지 않고
 * /user/queue/errors로 이유를 돌려줄 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveChatChannelInterceptor implements ChannelInterceptor {

    private static final Set<String> SUBSCRIBABLE = Set.of(
            LiveChatDestinations.TOPIC,
            LiveChatDestinations.USER_ERRORS
    );
    private static final Set<String> SENDABLE = Set.of(
            LiveChatDestinations.SEND
    );

    private final JwtUtil jwtUtil;
    private final AuthService authService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // 하트비트 등
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (command == StompCommand.SUBSCRIBE) {
            requireAllowed(SUBSCRIBABLE, accessor.getDestination());
        } else if (command == StompCommand.SEND) {
            requireAllowed(SENDABLE, accessor.getDestination());
        }
        return message;
    }

    /***
     * 함수 기능: CONNECT 프레임의 토큰으로 세션 사용자를 정한다. JwtAuthenticationFilter와 같은 규칙으로 검증한다.
     * @param bearerToken "Bearer {AT}" 또는 null
     * @return 토큰이 없으면 비로그인 principal
     */
    LiveChatPrincipal authenticate(String bearerToken) {

        if (!StringUtils.hasText(bearerToken)) {
            return LiveChatPrincipal.guest();
        }
        if (!bearerToken.startsWith("Bearer ")) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        CustomOAuth2User user;
        try {
            Claims claims = jwtUtil.parseClaims(bearerToken.substring(7));
            user = (CustomOAuth2User) jwtUtil.getAuthentication(claims).getPrincipal();
        } catch (ExpiredJwtException e) {
            throw new AuthException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        if (authService.isBlacklisted(user.getJti())) {
            throw new AuthException(AuthErrorCode.TOKEN_BLACKLISTED);
        }
        return LiveChatPrincipal.member(user);
    }

    private void requireAllowed(Set<String> allowed, String destination) {
        if (destination == null || !allowed.contains(destination)) {
            throw new LiveChatException(LiveChatErrorCode.FORBIDDEN_DESTINATION);
        }
    }
}
