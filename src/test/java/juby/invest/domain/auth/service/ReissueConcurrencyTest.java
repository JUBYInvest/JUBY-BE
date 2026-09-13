package juby.invest.domain.auth.service;

import juby.invest.domain.auth.dto.ReissueDto;
import juby.invest.domain.auth.exception.AuthException;
import juby.invest.domain.auth.exception.code.AuthErrorCode;
import juby.invest.domain.auth.repository.RefreshTokenRepository;
import juby.invest.domain.auth.util.TokenHasher;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.enums.Role;
import juby.invest.domain.member.enums.SocialType;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.global.security.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동일한 RT로 재발급 요청이 동시에 들어왔을 때 성공이 정확히 한 건인지 확인한다.
 * <p>
 * 락이 없으면 두 트랜잭션이 각자 같은 저장 해시를 읽고 둘 다 검증을 통과한다.
 * AT가 두 개 발급되고, 나중에 UPDATE한 쪽이 앞선 쪽의 RT를 덮어쓴다.
 * 먼저 성공한 사용자는 그 사실을 모른 채 다음 재발급에서야 401을 받는다.
 * 무엇보다 "이 RT는 이미 쓰였다"를 잡아내는 로테이션의 목적 자체가 무력화되어,
 * 탈취한 RT로 정상 사용자와 경쟁하면 둘 다 성공한다.
 * <p>
 * 이 테스트는 트랜잭션을 열지 않는다. @Transactional을 붙이면 롤백되어
 * 스레드들이 서로의 커밋을 보지 못하고 경합 자체가 재현되지 않는다.
 * 그래서 정리는 @AfterEach가 직접 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("RT 재발급 동시성 (동일 RT 동시 요청)")
class ReissueConcurrencyTest {

    @Autowired private AuthService authService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private JwtUtil jwtUtil;

    private Member member;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        member = saveMember("naver-1", "강민");
        refreshToken = issueRTFor(member);
    }

    @AfterEach
    void tearDown() {
        // 롤백이 없으므로 직접 지운다. RT가 member를 참조하므로 순서를 지킨다.
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();
    }

    private Member saveMember(String providerId, String name) {
        return memberRepository.save(Member.builder()
                .email(providerId + "@juby.com")
                .name(name)
                .socialType(SocialType.NAVER)
                .providerId(providerId)
                .role(Role.USER)
                .build());
    }

    /** 회원의 RT를 실제로 발급해 DB에 커밋해 둔다. */
    private String issueRTFor(Member owner) {
        String rawToken = jwtUtil.createRefreshToken(owner.getId());
        authService.saveOrUpdateRT(owner, rawToken);
        return rawToken;
    }

    /**
     * 저장된 해시를 읽는다.
     * findByMember는 비관적 락이 걸려 있어 트랜잭션 밖에서 부르면
     * TransactionRequiredException이 나므로 락 없는 findAll로 조회한다.
     */
    private String storedHashOf(Member owner) {
        return refreshTokenRepository.findAll().stream()
                .filter(rt -> rt.getMember().getId().equals(owner.getId()))
                .findFirst()
                .orElseThrow()
                .getToken();
    }

    private record Outcome(List<ReissueDto.ReissueRes> successes, List<Throwable> failures) {}

    /** 스레드들을 출발선에 모아 뒀다가 한꺼번에 풀어 준다. */
    private Outcome reissueTogether(int threadCount, List<String> tokens) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        List<ReissueDto.ReissueRes> successes = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < threadCount; i++) {
                String token = tokens.get(i % tokens.size());
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        // 그냥 submit만 하면 첫 스레드가 끝난 뒤 두 번째가 시작될 수 있다.
                        // 여기 모였다가 동시에 출발해야 경합이 재현된다.
                        start.await();
                        successes.add(authService.reissue(token));
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(5, TimeUnit.SECONDS))
                    .as("모든 스레드가 출발선에 모여야 한다").isTrue();
            start.countDown();

            // 락이 잘못 걸리면 교착 상태로 멈춘다. 테스트가 매달리지 않고 실패하게 한다.
            assertThat(done.await(30, TimeUnit.SECONDS))
                    .as("교착 상태 없이 모든 요청이 끝나야 한다").isTrue();
        } finally {
            pool.shutdownNow();
        }

        return new Outcome(successes, failures);
    }

    @Test
    @DisplayName("동시 2건 중 한 건만 성공하고 나머지는 INVALID_TOKEN이다")
    void onlyOneSucceedsAmongTwo() throws InterruptedException {
        Outcome outcome = reissueTogether(2, List.of(refreshToken));

        assertThat(outcome.successes()).hasSize(1);
        assertThat(outcome.failures()).hasSize(1);

        // 에러 코드까지 단정하는 게 이 테스트의 핵심이다.
        // 락이 없어도 DB의 write-write 충돌로 실패가 1건 날 수 있지만,
        // 그건 500으로 나가는 인프라 예외지 "이미 쓰인 RT"라는 도메인 판정이 아니다.
        assertThat(outcome.failures().getFirst())
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("동시 5건이어도 성공은 정확히 한 건이다")
    void onlyOneSucceedsAmongMany() throws InterruptedException {
        Outcome outcome = reissueTogether(5, List.of(refreshToken));

        assertThat(outcome.successes()).hasSize(1);
        assertThat(outcome.failures()).hasSize(4);
        assertThat(outcome.failures())
                .allSatisfy(e -> assertThat(e)
                        .isInstanceOf(AuthException.class)
                        .extracting(x -> ((AuthException) x).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_TOKEN));
    }

    @Test
    @DisplayName("DB에 남은 해시는 성공한 요청의 RT와 일치한다")
    void storedTokenBelongsToTheWinner() throws InterruptedException {
        Outcome outcome = reissueTogether(5, List.of(refreshToken));

        String winnerRT = outcome.successes().getFirst().refreshToken();

        // 진 요청이 자기 RT로 덮어썼다면 승자는 응답으로 받은 RT를 들고 있는데
        // DB에는 없는 상태가 된다. 그 사실이 다음 재발급에서야 드러난다.
        assertThat(storedHashOf(member)).isEqualTo(TokenHasher.hash(winnerRT));
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("성공한 요청의 RT로는 다음 재발급이 이어진다")
    void winnerSessionSurvives() throws InterruptedException {
        Outcome outcome = reissueTogether(2, List.of(refreshToken));
        String winnerRT = outcome.successes().getFirst().refreshToken();

        // 경합을 이긴 사용자의 세션은 멀쩡해야 한다. 로테이션 사슬이 끊기지 않았는지 본다.
        ReissueDto.ReissueRes next = authService.reissue(winnerRT);

        assertThat(next.accessToken()).isNotBlank();
        assertThat(storedHashOf(member)).isEqualTo(TokenHasher.hash(next.refreshToken()));
    }

    @Test
    @DisplayName("진 요청은 새 AT를 받지 못한다")
    void loserGetsNoAccessToken() throws InterruptedException {
        Outcome outcome = reissueTogether(3, List.of(refreshToken));

        // 성공 응답 수가 곧 발급된 AT 수다. 실패한 요청이 AT를 들고 나가면 안 된다.
        assertThat(outcome.successes()).hasSize(1);
        assertThat(outcome.successes().getFirst().accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("서로 다른 회원의 재발급은 서로 간섭하지 않는다")
    void differentMembersDoNotBlockEachOther() throws InterruptedException {
        Member other = saveMember("naver-2", "타인");
        String otherRT = issueRTFor(other);

        Outcome outcome = reissueTogether(2, List.of(refreshToken, otherRT));

        // 회원마다 RT row가 따로이므로 둘 다 성공해야 한다.
        // 한쪽이 실패하거나 교착이 나면 락 범위가 잘못 잡힌 것이다.
        assertThat(outcome.successes()).hasSize(2);
        assertThat(outcome.failures()).isEmpty();
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
        assertThat(storedHashOf(member)).isNotEqualTo(storedHashOf(other));
    }
}