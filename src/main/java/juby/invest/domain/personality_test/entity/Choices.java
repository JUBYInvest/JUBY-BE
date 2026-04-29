package juby.invest.domain.personality_test.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@Table(name = "choices")
public class Choices {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private PersonalityTest personalityTest;

    @Column(name = "content")
    private String content;

    @Column(name = "stable_score")
    private int stableScore;

    @Column(name = "stable_persue_score")
    private int stablePersueScore;

    @Column(name = "risk_neutral_score")
    private int riskNeutralScore;

    @Column(name = "active_invest_score")
    private int activeInvestScore;

    @Column(name = "aggressive_score")
    private int aggressiveScore;
}
