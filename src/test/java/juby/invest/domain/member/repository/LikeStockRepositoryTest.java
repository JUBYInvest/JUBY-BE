package juby.invest.domain.member.repository;

import juby.invest.domain.member.entity.LikeStock;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.stock.entity.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("LikeStockRepository")
class
LikeStockRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private LikeStockRepository likeStockRepository;

    private Member member;
    private Member otherMember;
    private Stock stock;

    @BeforeEach
    void setUp() {
        member = em.persistAndFlush(Member.builder().name("회원1").build());
        otherMember = em.persistAndFlush(Member.builder().name("회원2").build());
        stock = em.persistAndFlush(Stock.builder().stockCode("005930").stockName("삼성전자").build());
    }

    @Nested
    @DisplayName("유니크 제약 (member_id, stock_code)")
    class UniqueConstraint {

        @Test
        @DisplayName("같은 회원이 같은 종목을 두 번 등록하면 DataIntegrityViolationException이 발생한다")
        void rejectsDuplicateLike() {
            em.persistAndFlush(LikeStock.builder().member(member).stock(stock).build());

            LikeStock duplicate = LikeStock.builder().member(member).stock(stock).build();

            // saveAndFlush는 Repository 프록시를 거치므로, 서비스가 실제로 잡아내는
            // DataIntegrityViolationException으로 변환되는지까지 검증한다.
            assertThatThrownBy(() -> likeStockRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("서로 다른 회원은 같은 종목을 각자 등록할 수 있다")
        void allowsSameStockForDifferentMembers() {
            em.persistAndFlush(LikeStock.builder().member(member).stock(stock).build());

            LikeStock other = LikeStock.builder().member(otherMember).stock(stock).build();

            assertThat(likeStockRepository.saveAndFlush(other).getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByMemberIdAndStockCodeWithStock")
    class FindByMemberIdAndStockCode {

        @Test
        @DisplayName("등록된 관심 종목을 stock과 함께 조회한다")
        void findsExistingLikeStock() {
            em.persistAndFlush(LikeStock.builder().member(member).stock(stock).build());
            em.clear();

            Optional<LikeStock> found = likeStockRepository
                    .findByMemberIdAndStockCodeWithStock(member.getId(), stock.getStockCode());

            assertThat(found).isPresent();
            assertThat(found.get().getStock().getStockName()).isEqualTo("삼성전자");
        }

        @Test
        @DisplayName("등록되지 않은 조합이면 빈 값을 반환한다")
        void returnsEmptyWhenNotLiked() {
            Optional<LikeStock> found = likeStockRepository
                    .findByMemberIdAndStockCodeWithStock(member.getId(), stock.getStockCode());

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("다른 회원이 등록한 관심 종목은 조회되지 않는다")
        void doesNotLeakOtherMembersLike() {
            em.persistAndFlush(LikeStock.builder().member(otherMember).stock(stock).build());

            Optional<LikeStock> found = likeStockRepository
                    .findByMemberIdAndStockCodeWithStock(member.getId(), stock.getStockCode());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByMemberIdWithStock")
    class FindAllByMemberId {

        @Test
        @DisplayName("회원이 등록한 관심 종목만 stock과 함께 조회한다")
        void findsOnlyOwnLikeStocks() {
            Stock stock2 = em.persistAndFlush(Stock.builder().stockCode("000660").stockName("SK하이닉스").build());
            em.persistAndFlush(LikeStock.builder().member(member).stock(stock).build());
            em.persistAndFlush(LikeStock.builder().member(member).stock(stock2).build());
            em.persistAndFlush(LikeStock.builder().member(otherMember).stock(stock).build());
            em.clear();

            List<LikeStock> result = likeStockRepository.findAllByMemberIdWithStock(member.getId());

            assertThat(result).hasSize(2)
                    .extracting(ls -> ls.getStock().getStockCode())
                    .containsExactlyInAnyOrder("005930", "000660");
        }

        @Test
        @DisplayName("관심 종목이 없으면 빈 리스트를 반환한다")
        void returnsEmptyListWhenNoLikes() {
            List<LikeStock> result = likeStockRepository.findAllByMemberIdWithStock(member.getId());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findStockCodesByMemberId")
    class FindStockCodesByMemberId {

        @Test
        @DisplayName("회원이 좋아요한 종목코드만 Set으로 반환한다")
        void returnsOnlyOwnStockCodes() {
            Stock stock2 = em.persistAndFlush(Stock.builder().stockCode("000660").stockName("SK하이닉스").build());
            em.persistAndFlush(LikeStock.builder().member(member).stock(stock).build());
            em.persistAndFlush(LikeStock.builder().member(otherMember).stock(stock2).build());

            Set<String> codes = likeStockRepository.findStockCodesByMemberId(member.getId());

            assertThat(codes).containsExactly("005930");
        }

        @Test
        @DisplayName("관심 종목이 없으면 빈 Set을 반환한다")
        void returnsEmptySetWhenNoLikes() {
            Set<String> codes = likeStockRepository.findStockCodesByMemberId(member.getId());

            assertThat(codes).isEmpty();
        }
    }
}