package juby.invest.domain.community.entity;

import jakarta.persistence.*;
import juby.invest.domain.member.entity.Member;
import juby.invest.global.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 댓글은 한 단계만 지원한다. 대댓글이 필요해지면 parent_id 컬럼을 추가해 확장한다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "comment", indexes = {
        // 게시물별 작성순 조회(where post_id = ? and id > ? order by id)용
        @Index(name = "idx_comment_post_id_id", columnList = "post_id, id")
})
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    // 증감은 CommentRepository의 벌크 UPDATE로만 한다.
    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Comment(Post post, Member member, String content) {
        this.post = post;
        this.member = member;
        this.content = content;
    }

    public void update(String content) {
        this.content = content;
    }

    // 삭제 처리한다. 대댓글이 없으므로 "삭제된 댓글입니다"를 남기지 않고 목록에서 숨긴다.
    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public boolean isWrittenBy(Long memberId) {
        return memberId != null && memberId.equals(this.member.getId());
    }
}
