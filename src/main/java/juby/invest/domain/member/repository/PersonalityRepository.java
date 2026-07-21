package juby.invest.domain.member.repository;

import juby.invest.domain.member.entity.Personality;
import juby.invest.domain.member.enums.InvestPersonality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonalityRepository extends JpaRepository<Personality, Long> {
    Personality findByInvestPersonality(InvestPersonality personality);
}
