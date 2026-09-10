package juby.invest.domain.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import juby.invest.domain.auth.dto.ReissueDto;
import juby.invest.domain.auth.entity.AccessTokenBlacklist;
import juby.invest.domain.auth.entity.RefreshToken;
import juby.invest.domain.auth.exception.AuthException;
import juby.invest.domain.auth.exception.code.AuthErrorCode;
import juby.invest.domain.auth.repository.AccessTokenBlacklistRepository;
import juby.invest.domain.auth.repository.RefreshTokenRepository;
import juby.invest.domain.auth.util.TokenHasher;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.global.security.entity.CustomOAuth2User;
import juby.invest.global.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AuthService {

    private final MemberRepository memberRepository;
    private final AccessTokenBlacklistRepository accessTokenBlacklistRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

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

    /***
     * 함수 기능: 전달받은 RT를 검증하고 새로운 AT, RT를 발급한다.
     * @param refreshToken 회원 RT
     * @return 재발급된 AT와 RT 원문
     */
    @Transactional
    public ReissueDto.ReissueRes reissue(String refreshToken) {

        // 쿠키 만료/삭제 등으로 RT가 아예 오지 않은 경우다. 형식 오류(400)가 아니라 인증 실패(401)로 다룬다.
        if (!StringUtils.hasText(refreshToken)){
            throw new AuthException(AuthErrorCode.RT_COOKIE_MISSING);
        }

        // RT 서명, 만료 검증
        Claims claims;
        try {
             claims = jwtUtil.parseClaims(refreshToken);
        } catch (ExpiredJwtException e){
            log.warn("만료된 토큰입니다. {}", e.getMessage());
            throw new AuthException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e){
            log.warn("유효하지 않은 토큰입니다. {}", e.getMessage());
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        } catch (Exception e){
            log.error("토큰 검증 중 오류가 발생했습니다.", e);
            throw new AuthException(AuthErrorCode.UNKNOWN_TOKEN_ERROR);
        }


        // 타입 확인
        if (!"refresh".equals(claims.get("typ", String.class))){
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        // 소유자 특정
        // 탈퇴한 회원에게는 새 AT를 발급하지 않는다.
        long memberId = Long.parseLong(claims.getSubject());
        Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        RefreshToken savedRT = refreshTokenRepository.findByMember(member)
                .orElseThrow(() -> new AuthException(AuthErrorCode.RT_NOT_FOUND));

        // 저장된 해시와 일치 검사
        if (!savedRT.getToken().equals(TokenHasher.hash(refreshToken))){
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        // 새 AT 발급 + RT 로테이션
        String accessToken = jwtUtil.createAccessToken(memberId, member.getRole().name(), member.getName());

        String newRefreshToken = jwtUtil.createRefreshToken(memberId);
        savedRT.updateToken(TokenHasher.hash(newRefreshToken), LocalDateTime.now().plusSeconds(rtValidity/1000));

        return new ReissueDto.ReissueRes(accessToken, newRefreshToken);
    }

    /***
     * 함수 기능: 회원의 AT를 블랙리스트에 추가하고, RT는 삭제한다.
     * @param user 회원 인증 객체
     */
    @Transactional
    public void logout(CustomOAuth2User user) {

        // 회원 AT를 블랙리스트에 저장한다.
        accessTokenBlacklistRepository.save(
                AccessTokenBlacklist.builder()
                        .jti(user.getJti())
                        .expiresAt(user.getExpiresAt())
                        .build());

        // 회원 RT를 삭제한다.
        refreshTokenRepository.deleteByMember_Id(user.getId());
    }

    /***
     * 함수 기능: 로그아웃/탈퇴로 무효화된 AT인지 확인한다.
     *          서명과 만료는 멀쩡하므로 토큰 파싱만으로는 걸러지지 않는다.
     * @param jti AT의 JWT ID
     * @return 블랙리스트 등록 여부
     */
    public boolean  isBlacklisted(String jti) {
        return accessTokenBlacklistRepository.existsById(jti);
    }
}
