package juby.invest.domain.community.entity;

import jakarta.persistence.*;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.global.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post", indexes = {
        // 종목별 최신순 조회(where stock_code = ? and id < ? order by id desc)용
        @Index(name = "idx_post_stock_code_id", columnList = "stock_code, id"),
        // 이번 주 인기글 조회(where created_at >= 이번 주 월요일)용
        @Index(name = "idx_post_created_at", columnList = "created_at")
})
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 종목을 고르지 않은 글은 null이다. 이런 글은 전체 목록에만 나온다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code")
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // 길이 제한은 PostReqDto(40/1000)에서 검증한다. 컬럼은 이전에 만든 DB(100/2000)와 맞춰 두고 줄이지 않는다.
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    // 목록에서 매번 세지 않도록 미리 저장해 둔다. 증감은 PostRepository의 벌크 UPDATE로만 한다.
    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Post(Stock stock, Member member, String title, String content) {
        this.stock = stock;
        this.member = member;
        this.title = title;
        this.content = content;
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    // 삭제 처리한다. 행은 남기고 삭제 시각만 기록한다.
    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public boolean hasStock() {
        return this.stock != null;
    }

    public boolean isWrittenBy(Long memberId) {
        return memberId != null && memberId.equals(this.member.getId());
    }
}
