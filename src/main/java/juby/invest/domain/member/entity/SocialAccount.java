package juby.invest.domain.member.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "social_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "provider")
    private String provider;

    @Column(name = "provider_id")
    private String provider_id;

    @Builder
    public SocialAccount(Member member, String provider, String provider_id) {
        this.member = member;
        this.provider = provider;
        this.provider_id = provider_id;
    }
}
