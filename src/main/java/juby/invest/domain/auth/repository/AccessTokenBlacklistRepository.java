package juby.invest.domain.auth.repository;

import juby.invest.domain.auth.entity.AccessTokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AccessTokenBlacklistRepository extends JpaRepository<AccessTokenBlacklist, String> {
    List<AccessTokenBlacklist> findAllByExpiresAtBefore(LocalDateTime expiresAtBefore);
}
