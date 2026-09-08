package juby.invest.domain.auth.service;

import juby.invest.domain.auth.entity.RefreshToken;
import juby.invest.domain.auth.repository.RefreshTokenRepository;
import juby.invest.domain.auth.util.TokenHasher;
import juby.invest.domain.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-validity}") private long rtValidity;

    /***
     * 함수 기능: 소셜 로그인 성공 후, 새로 발급된 RT를 DB에 저장/업데이트 한다.
     * @param member 회원 객체
     * @param refreshToken RT
     */
    @Transactional
    public void saveOrUpdateRT(Member member, String refreshToken) {

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(rtValidity/1000);

        refreshTokenRepository.findByMember(member)
                .ifPresentOrElse( // 기존 RT가 존재하면 값 업데이트
                        existingRT -> {
                            existingRT.updateToken(TokenHasher.hash(refreshToken), expiresAt);
                        },
                        () -> { // 기존 RT가 없다면 새로 저장
                            RefreshToken rt = RefreshToken.builder()
                                    .member(member)
                                    .token(TokenHasher.hash(refreshToken))
                                    .expiresAt(expiresAt)
                                    .build();
                            refreshTokenRepository.save(rt);
                        }
                );
    }
}
