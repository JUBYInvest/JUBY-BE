package juby.invest.domain.member.entity;

import jakarta.persistence.*;
import juby.invest.domain.member.enums.InvestPersonality;
import juby.invest.domain.member.enums.Role;
import juby.invest.domain.member.enums.SocialType;
import lombok.*;

import java.time.LocalDate;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personality_id")
    private Personality personality;

    @Column(name = "email")
    private String email;

    @Column(name = "name")
    private String name;

    @Column(name = "birth")
    private String birth;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Builder.Default()
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    private SocialType socialType;

    @Column(name = "provider_id")
    private String providerId;
}
