package juby.invest.domain.personality_test.repository;

import juby.invest.domain.personality_test.entity.Choices;
import juby.invest.domain.personality_test.entity.PersonalityTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChoiceRepository extends JpaRepository<Choices, Long> {
    List<Choices> findByPersonalityTest(PersonalityTest personalityTest);
}
