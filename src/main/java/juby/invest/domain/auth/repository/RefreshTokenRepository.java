package juby.invest.domain.auth.repository;

import jakarta.persistence.LockModeType;
import juby.invest.domain.auth.entity.RefreshToken;
import juby.invest.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    void deleteByMember_Id(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByMember(Member member);
}
