package juby.invest.domain.member.repository;

import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.enums.SocialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Member findByEmail(String email);

    Optional<Member> findBySocialTypeAndProviderId(SocialType socialType, String providerId);
}
