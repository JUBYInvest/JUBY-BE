package juby.invest.domain.community.entity;

import jakarta.persistence.*;
import juby.invest.domain.member.entity.Member;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 게시물 좋아요와 테이블을 나눈다. 하나로 합치면(target_type, target_id) FK를 걸 수 없다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "comment_like", uniqueConstraints = {
        // 한 회원은 한 댓글에 좋아요를 한 번만 누를 수 있다.
        @UniqueConstraint(name = "uk_comment_like_comment_member", columnNames = {"comment_id", "member_id"})
})
public class CommentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Builder
    public CommentLike(Comment comment, Member member) {
        this.comment = comment;
        this.member = member;
    }
}
