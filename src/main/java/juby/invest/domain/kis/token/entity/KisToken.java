package juby.invest.domain.kis.token.entity;

import jakarta.persistence.*;
import juby.invest.domain.kis.token.converter.TokenConverter;
import juby.invest.domain.kis.token.enums.TokenType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name = "kis_token")
public class KisToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TokenType tokenType;

    @Column(name = "token_value", nullable = false, length = 1000)
    @Convert(converter = TokenConverter.class)
    private String tokenValue;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    public void updateToken(String updateToken, LocalDateTime updateExpiredAt) {
        this.tokenValue = updateToken;
        this.expiredAt = updateExpiredAt;
    }
}
