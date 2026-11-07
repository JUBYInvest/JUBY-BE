package juby.invest.domain.livechat.entity;

import jakarta.persistence.*;
import juby.invest.domain.member.entity.Member;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/***
 * 홈 화면 실시간 채팅 메시지.
 * <p>
 * 올라온 메시지는 수정/삭제할 수 없으므로 updatedAt이 있는 BaseTimeEntity를 쓰지 않는다.
 * 최근 LiveChatService.MAX_STORED_MESSAGES개만 남기고 오래된 것부터 지운다.
 * 조회와 정리는 모두 PK(id) 순서로 하므로 별도 인덱스가 필요 없다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "live_chat_message")
public class LiveChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "content", nullable = false, length = 300)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public LiveChatMessage(Member member, String content) {
        this.member = member;
        this.content = content;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
