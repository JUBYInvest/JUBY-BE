package juby.invest.domain.chat.entity;

import jakarta.persistence.*;
import juby.invest.domain.member.entity.Member;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "chat_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "title")
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 답변 생성 시작 시각. null이 아니면 이 대화방에서 답변을 생성 중이라는 뜻(동시 질문 차단용).
    // 선점/해제는 ChatSessionRepository의 조건부 UPDATE 쿼리로만 변경한다. 엔티티 저장(touch 등) 시
    // 엔티티가 들고 있던 이전 값으로 덮어써 선점이 풀리지 않도록 insert/update 대상에서 제외한다.
    @Column(name = "answering_started_at", insertable = false, updatable = false)
    private LocalDateTime answeringStartedAt;

    @Builder
    public ChatSession(Member member, String title) {
        this.member = member;
        this.title = title;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateTitle(String title) {
        this.title = title;
        this.updatedAt = LocalDateTime.now();
    }

    // 새 메시지가 쌓일 때마다 대화방 최근 수정일을 갱신한다.
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}