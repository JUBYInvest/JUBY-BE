package juby.invest.domain.auth.repository;

import juby.invest.domain.auth.entity.AccessTokenBlacklist;
import juby.invest.global.scheduler.DeleteExpiredATScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("AT 블랙리스트 리포지토리 (등록 / 조회 / 만료 정리)")
class AccessTokenBlacklistRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private AccessTokenBlacklistRepository blacklistRepository;

    private void blacklist(String jti, LocalDateTime expiresAt) {
        blacklistRepository.save(AccessTokenBlacklist.builder()
                .jti(jti)
                .expiresAt(expiresAt)
                .build());
    }

    @Nested
    @DisplayName("등록과 조회")
    class SaveAndExists {

        @Test
        @DisplayName("등록 전에는 블랙리스트에 없다")
        void notBlacklistedBeforeLogout() {
            assertThat(blacklistRepository.existsById("some-jti")).isFalse();
        }

        @Test
        @DisplayName("등록한 jti는 블랙리스트로 조회된다")
        void blacklistedAfterLogout() {
            blacklist("logged-out-jti", LocalDateTime.now().plusMinutes(30));
            em.flush();
            em.clear();

            assertThat(blacklistRepository.existsById("logged-out-jti")).isTrue();
        }

        @Test
        @DisplayName("등록 시각(added_at)이 자동으로 채워진다")
        void recordsAddedAt() {
            LocalDateTime before = LocalDateTime.now();
            blacklist("jti-with-time", LocalDateTime.now().plusMinutes(30));
            em.flush();
            em.clear();

            AccessTokenBlacklist found = blacklistRepository.findById("jti-with-time").orElseThrow();

            assertThat(found.getAddedAt()).isNotNull();
            assertThat(found.getAddedAt()).isAfterOrEqualTo(before.minusSeconds(1));
        }

        @Test
        @DisplayName("같은 jti를 두 번 등록해도 행이 늘지 않는다")
        void doubleLogoutIsIdempotent() {
            // 로그아웃 요청이 중복으로 들어와도 PK 충돌로 500이 나면 안 된다.
            blacklist("same-jti", LocalDateTime.now().plusMinutes(30));
            em.flush();
            blacklist("same-jti", LocalDateTime.now().plusMinutes(30));
            em.flush();

            assertThat(blacklistRepository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("만료 행 정리")
    class Cleanup {

        @Test
        @DisplayName("expires_at이 지난 행만 삭제한다")
        void deletesOnlyExpired() {
            LocalDateTime now = LocalDateTime.now();
            blacklist("expired-1", now.minusHours(1));
            blacklist("expired-2", now.minusMinutes(1));
            blacklist("still-valid", now.plusMinutes(30));
            em.flush();

            int deleted = blacklistRepository.deleteExpired(now);

            assertThat(deleted).isEqualTo(2);
            assertThat(blacklistRepository.existsById("still-valid")).isTrue();
            assertThat(blacklistRepository.existsById("expired-1")).isFalse();
            assertThat(blacklistRepository.existsById("expired-2")).isFalse();
        }

        @Test
        @DisplayName("지울 행이 없으면 0을 반환한다")
        void returnsZeroWhenNothingExpired() {
            blacklist("still-valid", LocalDateTime.now().plusMinutes(30));
            em.flush();

            assertThat(blacklistRepository.deleteExpired(LocalDateTime.now())).isZero();
            assertThat(blacklistRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("정리된 AT는 더 이상 블랙리스트로 걸리지 않는다")
        void cleanedTokenIsNoLongerBlacklisted() {
            // 이미 자체 만료된 AT라 블랙리스트에서 빠져도 인증을 통과하지 못한다.
            // 즉 정리는 안전하며, 방치하면 테이블만 무한정 커진다.
            blacklist("expired-jti", LocalDateTime.now().minusHours(1));
            em.flush();

            blacklistRepository.deleteExpired(LocalDateTime.now());

            assertThat(blacklistRepository.existsById("expired-jti")).isFalse();
        }

        @Test
        @DisplayName("스케줄러가 만료 행을 정리한다")
        void schedulerCleansExpiredRows() {
            LocalDateTime now = LocalDateTime.now();
            blacklist("expired", now.minusDays(1));
            blacklist("valid", now.plusMinutes(30));
            em.flush();

            // 스케줄러는 @Profile("dev")라 테스트 컨텍스트에 빈으로 없다. 직접 조립해 동작만 확인한다.
            new DeleteExpiredATScheduler(blacklistRepository).deleteExpiredAT();
            em.clear();

            assertThat(blacklistRepository.count()).isEqualTo(1);
            assertThat(blacklistRepository.existsById("valid")).isTrue();
        }
    }
}