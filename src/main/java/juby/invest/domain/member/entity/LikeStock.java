package juby.invest.domain.member.entity;

import jakarta.persistence.*;
import juby.invest.domain.stock.entity.Stock;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "like_stock",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_like_stock_member_stock",
                columnNames = {"member_id", "stock_code"}))
public class LikeStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code")
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "liked_at", nullable = false)
    private LocalDateTime likedAt;

    @Builder
    public LikeStock(Stock stock, Member member) {
        this.stock = stock;
        this.member = member;
        this.likedAt = LocalDateTime.now();
    }
}
