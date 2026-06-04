package juby.invest.domain.kis.token.repository;

import juby.invest.domain.kis.token.entity.KisToken;
import juby.invest.domain.kis.token.enums.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface TokenRepository extends JpaRepository<KisToken, Long> {
    Optional<KisToken> findByTokenType(TokenType tokenType);
}
