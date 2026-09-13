package juby.invest.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "access_token_blacklist")
public class AccessTokenBlacklist {

    @Id
    @Column(name = "jti", length = 36)
    private String jti;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public AccessTokenBlacklist(String jti, LocalDateTime expiresAt) {
        this.jti = jti;
        this.addedAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }
}

