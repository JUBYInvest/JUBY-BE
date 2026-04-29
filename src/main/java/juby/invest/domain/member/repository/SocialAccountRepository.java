package juby.invest.domain.member.repository;

import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.entity.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    boolean existsByMemberAndProvider(Member member, String provider);
}

