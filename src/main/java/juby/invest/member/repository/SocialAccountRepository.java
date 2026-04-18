package juby.invest.member.repository;

import juby.invest.member.entity.Member;
import juby.invest.member.entity.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    boolean existsByMemberAndProvider(Member member, String provider);
}

