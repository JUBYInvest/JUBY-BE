package juby.invest.domain.chat.entity;

import jakarta.persistence.*;
import juby.invest.domain.chat.enums.ChatRole;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "chat_content")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 발신자(member)는 대화방(chatSession) 소유자로 항상 특정되므로 별도로 들고 있지 않는다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id")
    private ChatSession chatSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ChatRole role;

    // 긴 AI 답변도 저장할 수 있도록 LONGTEXT로 지정
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ChatContent(ChatSession chatSession, ChatRole role, String content) {
        this.chatSession = chatSession;
        this.role = role;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
}