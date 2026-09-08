package juby.invest.domain.auth.repository;

import juby.invest.domain.auth.entity.AccessTokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AccessTokenBlacklistRepository extends JpaRepository<AccessTokenBlacklist, String> {

    @Modifying(clearAutomatically = true)
    @Query("""
        delete from AccessTokenBlacklist b
        where b.expiresAt < :now
    """)
    int deleteExpired(LocalDateTime now);
}
