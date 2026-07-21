package juby.invest.domain.personality_test.repository;

import juby.invest.domain.personality_test.entity.PersonalityTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonalityTestRepository extends JpaRepository<PersonalityTest, Long> {
}
