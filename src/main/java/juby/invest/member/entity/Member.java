package juby.invest.member.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import juby.invest.member.enums.InvestPersonality;
import juby.invest.member.enums.Role;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email")
    private String email;

    @Column(name = "nickname")
    private String nickName;

    @Column(name = "profile_img")
    private String profileImg;

    @Column(name = "birth")
    private String birth;

    @Enumerated(EnumType.STRING)
    @Column(name = "invest_personality")
    private InvestPersonality investPersonality;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;

    @Builder

    public Member(String email, String nickName, String profileImg, String birth, Role role) {
        this.email = email;
        this.nickName = nickName;
        this.profileImg = profileImg;
        this.birth = birth;
        this.investPersonality = null;
        this.role = role;
    }
}
