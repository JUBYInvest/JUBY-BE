package juby.invest.domain.community.service;

import juby.invest.domain.community.dto.CommunityStockResDto;
import juby.invest.domain.community.dto.PostReqDto;
import juby.invest.domain.community.dto.PostResDto;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.stock.entity.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

/***
 * 종목별 글 수 집계는 쿼리가 핵심이라 H2 위에서 돌리고, KIS 시세 캐시만 목으로 바꾼다.
 */
@DataJpaTest
@Import({CommunityBoardService.class, PostService.class})
@DisplayName("CommunityBoardService")
class CommunityBoardServiceTest {

    private static final LocalDateTime EARLY = LocalDateTime.of(2026, 11, 5, 11, 25);
    private static final LocalDateTime LATE = LocalDateTime.of(2026, 11, 5, 11, 30);

    @Autowired private TestEntityManager em;
    @Autowired private CommunityBoardService communityBoardService;
    @Autowired private PostService postService;
    @MockitoBean private ChangeRateCache changeRateCache;

    private Member writer;

    @BeforeEach
    void setUp() {
        writer = em.persist(Member.builder().name("작성자").nickname("용감한개미").build());
        em.persist(Stock.builder().stockCode("005930").stockName("삼성전자").build());
        em.persist(Stock.builder().stockCode("000660").stockName("SK하이닉스").build());
        em.flush();

        given(changeRateCache.get(anyCollection())).willReturn(Map.of(
                "005930", new ChangeRateCache.ChangeRate(-5.05, LATE),
                "000660", new ChangeRateCache.ChangeRate(null, EARLY) // 시세 조회 실패
        ));
    }

    private Long write(String stockCode) {
        return postService.createPost(writer.getId(), new PostReqDto.CreateReq(stockCode, "제목", "내용")).postId();
    }

    @Test
    @DisplayName("전체 글 수는 종목 없는 글을 포함하고, 종목 카드는 글 많은 순이며 삭제된 글은 세지 않는다")
    void stockBoard() {
        write("005930");
        write("000660");
        write("000660");
        Long deleted = write("000660");
        write(null);
        postService.deletePost(writer.getId(), deleted);

        CommunityStockResDto.StockBoard board = communityBoardService.getStockBoard();

        assertThat(board.totalPostCount()).isEqualTo(4);
        assertThat(board.stocks()).extracting(CommunityStockResDto.StockCard::stockName).containsExactly("SK하이닉스", "삼성전자");
        assertThat(board.stocks()).extracting(CommunityStockResDto.StockCard::postCount).containsExactly(2L, 1L);
        assertThat(board.stocks().get(1).changeRate()).isEqualTo(-5.05);
        assertThat(board.stocks().get(0).changeRate()).isNull();
        // 실패한 종목의 시각(EARLY)은 기준 시각에 쓰지 않는다.
        assertThat(board.priceAsOf()).isEqualTo(LATE);
    }

    @Test
    @DisplayName("목록의 게시물에 종목 등락률을 붙이고, 종목 없는 글은 비워 둔다")
    void feedChangeRates() {
        write("005930");
        write(null);

        assertThat(communityBoardService.getLatestPosts(null, null, null, 20).posts())
                .extracting(PostResDto.PostSummary::changeRate)
                .containsExactly(null, -5.05);
        assertThat(communityBoardService.getPopularPosts(null, null, null, null, 20).posts())
                .extracting(PostResDto.PostSummary::changeRate)
                .containsExactlyInAnyOrder(null, -5.05);
    }
}
