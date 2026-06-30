package juby.invest.domain.member.entity;

import jakarta.persistence.*;
import juby.invest.domain.member.enums.InvestPersonality;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "personality")
@Entity
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Personality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invest_personality")
    @Enumerated(EnumType.STRING)
    private InvestPersonality investPersonality;

    @Column(name = "description")
    private String description;

    @Column(name = "personality_img")
    private String personalityImg;
}
