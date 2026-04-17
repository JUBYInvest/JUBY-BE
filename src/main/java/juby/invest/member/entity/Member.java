package juby.invest.member.entity;

import jakarta.persistence.*;
import juby.invest.domain.Stock;
import juby.invest.member.enums.InvestPersonality;
import juby.invest.member.enums.Role;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
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
    private LocalDateTime birth;

    @Column(name = "invest_personality")
    private Enum<InvestPersonality> investPersonality;

    @Column(name = "role")
    private Enum<Role> role;
}
