package juby.invest.member.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import juby.invest.member.enums.InvestPersonality;
import juby.invest.member.enums.Role;
import lombok.AccessLevel;
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
    @NotNull
    private String email;

    @Column(name = "nickname")
    @NotNull
    private String nickName;

    @Column(name = "profile_img")
    @NotNull
    private String profileImg;

    @Column(name = "birth")
    @NotNull
    private LocalDate birth;

    @Column(name = "invest_personality")
    private Enum<InvestPersonality> investPersonality;

    @Column(name = "role")
    private Enum<Role> role;
}
