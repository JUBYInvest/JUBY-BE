package juby.invest.domain.member.entity;

import jakarta.persistence.*;
import juby.invest.domain.member.dto.ChangeMemberInfo;
import juby.invest.domain.member.enums.InvestPersonality;
import juby.invest.domain.member.enums.Role;
import juby.invest.domain.member.enums.SocialType;
import lombok.*;

import java.time.LocalDate;

@Entity
@Builder
@Getter
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
    private LocalDate birth;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Builder.Default()
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    private SocialType socialType;

    @Column(name = "provider_id")
    private String providerId;

    public void updatePersonality(Personality personality){
        this.personality = personality;
    }

    public void updateInfo(ChangeMemberInfo.ChangeInfoReq dto){
        if (dto.name() != null){
            this.name = dto.name();
        }
        if (dto.birth() != null){
            this.birth = dto.birth();
        }
    }
}
