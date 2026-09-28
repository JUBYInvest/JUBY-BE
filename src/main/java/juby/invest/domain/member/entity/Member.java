package juby.invest.domain.member.entity;

import jakarta.persistence.*;
import juby.invest.domain.member.dto.ChangeMemberInfo;
import juby.invest.domain.member.enums.Role;
import juby.invest.domain.member.enums.SocialType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Column(name = "is_onboarded", nullable = false)
    @Builder.Default
    private boolean isOnboarded = false;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 온보딩을 참으로 변경한다.
    public void completeOnboard(){
        this.isOnboarded = true;
    }

    // 탈퇴 처리한다. 행은 남기고 탈퇴 시각만 기록한다.
    public void withdraw(){
        this.deletedAt = LocalDateTime.now();
    }

    // 탈퇴한 회원인지 확인한다.
    public boolean isDeleted(){
        return this.deletedAt != null;
    }

    // 회원의 투자 성향을 업데이트 한다.
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
