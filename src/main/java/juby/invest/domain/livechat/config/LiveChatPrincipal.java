package juby.invest.domain.livechat.config;

import juby.invest.global.security.entity.CustomOAuth2User;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.UUID;

/***
 * STOMP 세션의 사용자.
 * <p>
 * 비로그인 세션에도 principal을 붙인다. 그래야 비로그인 사용자가 메시지를 보냈을 때도
 * /user/queue/errors로 "로그인이 필요하다"는 응답을 돌려줄 수 있다. (user 목적지는 principal이 없으면 쓸 수 없다)
 * <p>
 * getName()은 user 목적지의 키라 회원마다 달라야 한다. CustomOAuth2User.getName()은 실명이라 겹칠 수 있으므로
 * 회원 ID를 쓴다.
 *
 * @param memberId 비로그인이면 null
 * @param jti 블랙리스트(로그아웃/탈퇴) 확인용. 비로그인이면 null
 * @param expiresAt AT 만료 시각. 연결은 AT보다 오래 살 수 있으므로 보낼 때마다 확인한다. 비로그인이면 null
 */
public record LiveChatPrincipal(Long memberId, String jti, LocalDateTime expiresAt, String name) implements Principal {

    public static LiveChatPrincipal member(CustomOAuth2User user) {
        return new LiveChatPrincipal(user.getId(), user.getJti(), user.getExpiresAt(), String.valueOf(user.getId()));
    }

    public static LiveChatPrincipal guest() {
        return new LiveChatPrincipal(null, null, null, "guest-" + UUID.randomUUID());
    }

    public boolean isGuest() {
        return memberId == null;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    @Override
    public String getName() {
        return name;
    }
}
