package juby.invest.domain.community.service;

import juby.invest.domain.community.dto.CommunityStockResDto;
import juby.invest.domain.community.dto.PostResDto;
import juby.invest.domain.community.repository.PostRepository;
import juby.invest.domain.community.repository.StockPostCount;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/***
 * 커뮤니티 목록 화면용 조회를 모은다. DB 조회 결과에 KIS 등락률을 붙인다.
 * KIS 호출이 DB 트랜잭션(커넥션)을 붙잡지 않도록, 이 클래스는 일부러 @Transactional을 걸지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CommunityBoardService {

    private static final int STOCK_CARD_LIMIT = 10;

    private final PostService postService;
    private final PostRepository postRepository;
    private final ChangeRateCache changeRateCache;

    /***
     * 함수 기능: 최신순 게시물 목록에 종목 등락률을 붙여 반환한다.
     */
    public PostResDto.PostList getLatestPosts(Long viewerId, String stockCode, Long cursor, int size) {
        PostResDto.PostList list = postService.getPosts(viewerId, stockCode, cursor, size);
        return list.toBuilder().posts(withChangeRates(list.posts())).build();
    }

    /***
     * 함수 기능: 이번 주 인기 게시물 목록에 종목 등락률을 붙여 반환한다.
     */
    public PostResDto.PopularPostList getPopularPosts(Long viewerId, String stockCode, Integer cursorLikeCount, Long cursorId, int size) {
        PostResDto.PopularPostList list = postService.getPopularPosts(viewerId, stockCode, cursorLikeCount, cursorId, size);
        return list.toBuilder().posts(withChangeRates(list.posts())).build();
    }

    /***
     * 함수 기능: 상단 종목 카드 줄을 조회한다. 전체 글 수와, 글이 많은 종목 순으로 최대 10개 종목의 글 수/등락률을 반환한다.
     */
    public CommunityStockResDto.StockBoard getStockBoard() {
        long total = postRepository.countByDeletedAtIsNull();
        List<StockPostCount> counts = postRepository.countByStock(PageRequest.ofSize(STOCK_CARD_LIMIT));

        Map<String, ChangeRateCache.ChangeRate> rates =
                changeRateCache.get(counts.stream().map(StockPostCount::getStockCode).toList());

        List<CommunityStockResDto.StockCard> cards = counts.stream()
                .map(count -> CommunityStockResDto.StockCard.builder()
                        .stockCode(count.getStockCode())
                        .stockName(count.getStockName())
                        .postCount(count.getPostCount())
                        .changeRate(rates.get(count.getStockCode()).rate())
                        .build())
                .toList();

        // 종목마다 가져온 시각이 다를 수 있으므로, 가장 오래된 값을 기준 시각으로 보여준다.
        LocalDateTime priceAsOf = rates.values().stream()
                .filter(rate -> rate.rate() != null)
                .map(ChangeRateCache.ChangeRate::fetchedAt)
                .min(Comparator.naturalOrder())
                .orElse(null);

        return CommunityStockResDto.StockBoard.builder()
                .totalPostCount(total)
                .stocks(cards)
                .priceAsOf(priceAsOf)
                .build();
    }

    private List<PostResDto.PostSummary> withChangeRates(List<PostResDto.PostSummary> posts) {
        Map<String, ChangeRateCache.ChangeRate> rates = changeRateCache.get(
                posts.stream().map(PostResDto.PostSummary::stockCode).filter(Objects::nonNull).toList());

        return posts.stream()
                .map(post -> post.stockCode() == null ? post
                        : post.toBuilder().changeRate(rates.get(post.stockCode()).rate()).build())
                .toList();
    }
}
