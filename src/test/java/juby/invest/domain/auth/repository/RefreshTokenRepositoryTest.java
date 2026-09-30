package juby.invest.domain.auth.repository;

import juby.invest.domain.auth.entity.RefreshToken;
import juby.invest.domain.auth.util.TokenHasher;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.enums.Role;
import juby.invest.domain.member.enums.SocialType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("RT 리포지토리 (저장 / 조회 / 로테이션 / 삭제)")
class RefreshTokenRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        member = em.persist(Member.builder()
                .email("kangmin@juby.com")
                .name("강민")
                .socialType(SocialType.NAVER)
                .providerId("naver-1")
                .role(Role.USER)
                .build());
    }

    private Member anotherMember() {
        return em.persist(Member.builder()
                .email("other@juby.com")
                .name("타인")
                .socialType(SocialType.GOOGLE)
                .providerId("google-1")
                .role(Role.USER)
                .build());
    }

    private RefreshToken saveRT(Member owner, String rawToken) {
        return refreshTokenRepository.save(RefreshToken.builder()
                .member(owner)
                .token(TokenHasher.hash(rawToken))
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build());
    }

    @Nested
    @DisplayName("저장과 조회")
    class SaveAndFind {

        @Test
        @DisplayName("저장한 RT를 회원으로 조회한다")
        void findsByMember() {
            saveRT(member, "raw-refresh-token");
            em.flush();
            em.clear();

            Optional<RefreshToken> found = refreshTokenRepository.findByMember(member);

            assertThat(found).isPresent();
            assertThat(found.get().getToken()).isEqualTo(TokenHasher.hash("raw-refresh-token"));
        }

        @Test
        @DisplayName("RT는 원문이 아니라 해시로만 저장된다")
        void storesHashNotRaw() {
            String raw = "raw-refresh-token";
            saveRT(member, raw);
            em.flush();
            em.clear();

            RefreshToken found = refreshTokenRepository.findByMember(member).orElseThrow();

            // DB가 통째로 유출돼도 RT 원문은 복원되지 않아야 한다.
            assertThat(found.getToken()).isNotEqualTo(raw);
            // SHA-256을 패딩 없는 Base64 URL로 인코딩하면 43자다. 컬럼 length와 일치해야 잘리지 않는다.
            assertThat(found.getToken()).hasSize(43);
        }

        @Test
        @DisplayName("다른 회원의 RT는 조회되지 않는다")
        void doesNotLeakOtherMembersToken() {
            saveRT(member, "mine");
            Member other = anotherMember();
            em.flush();
            em.clear();

            assertThat(refreshTokenRepository.findByMember(other)).isEmpty();
        }

        @Test
        @DisplayName("한 회원은 RT를 하나만 가진다")
        void enforcesOneTokenPerMember() {
            saveRT(member, "first");
            em.flush();

            // member_id에 unique 제약이 걸려 있어야 "회원당 활성 세션 1개" 정책이 DB에서 보장된다.
            // IDENTITY 전략이라 save 시점에 INSERT가 나가고, 거기서 바로 제약에 걸린다.
            assertThatThrownBy(() -> saveRT(member, "second"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("로테이션")
    class Rotation {

        @Test
        @DisplayName("재발급 시 새 row가 아니라 기존 row가 갱신된다")
        void updatesInPlace() {
            RefreshToken saved = saveRT(member, "old-token");
            em.flush();
            Long originalId = saved.getId();

            LocalDateTime newExpiry = LocalDateTime.now().plusDays(14).withNano(0);
            saved.updateToken(TokenHasher.hash("new-token"), newExpiry);
            em.flush();
            em.clear();

            RefreshToken reloaded = refreshTokenRepository.findByMember(member).orElseThrow();

            // row가 하나 더 생기면 예전 RT가 살아남아 두 기기가 동시에 유효해진다.
            assertThat(refreshTokenRepository.count()).isEqualTo(1);
            assertThat(reloaded.getId()).isEqualTo(originalId);
            assertThat(reloaded.getToken()).isEqualTo(TokenHasher.hash("new-token"));
            assertThat(reloaded.getExpiresAt()).isEqualTo(newExpiry);
        }

        @Test
        @DisplayName("로테이션 후 예전 RT의 해시로는 더 이상 일치하지 않는다")
        void invalidatesPreviousToken() {
            RefreshToken saved = saveRT(member, "old-token");
            em.flush();

            saved.updateToken(TokenHasher.hash("new-token"), LocalDateTime.now().plusDays(14));
            em.flush();
            em.clear();

            RefreshToken reloaded = refreshTokenRepository.findByMember(member).orElseThrow();

            // AuthService.reissue가 하는 비교와 같은 형태다. 재사용된 RT는 여기서 걸러진다.
            assertThat(reloaded.getToken()).isNotEqualTo(TokenHasher.hash("old-token"));
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("회원 ID로 RT를 삭제한다")
        void deletesByMemberId() {
            saveRT(member, "raw");
            em.flush();

            refreshTokenRepository.deleteByMember_Id(member.getId());
            em.flush();
            em.clear();

            assertThat(refreshTokenRepository.findByMember(member)).isEmpty();
        }

        @Test
        @DisplayName("RT가 없는 회원을 삭제해도 예외가 나지 않는다")
        void deleteIsIdempotent() {
            // 로그아웃을 두 번 호출하거나, RT 없이 탈퇴하는 경우를 위한 보장이다.
            refreshTokenRepository.deleteByMember_Id(member.getId());
            em.flush();

            assertThat(refreshTokenRepository.findByMember(member)).isEmpty();
        }

        @Test
        @DisplayName("RT를 지워도 회원 행은 남는다")
        void keepsMemberRow() {
            saveRT(member, "raw");
            em.flush();

            refreshTokenRepository.deleteByMember_Id(member.getId());
            em.flush();
            em.clear();

            assertThat(em.find(Member.class, member.getId())).isNotNull();
        }
    }
}