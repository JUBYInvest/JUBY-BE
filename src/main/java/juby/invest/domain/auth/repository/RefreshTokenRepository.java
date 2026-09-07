package juby.invest.domain.auth.repository;

import juby.invest.domain.auth.entity.RefreshToken;
import juby.invest.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByMember(Member member);
}
