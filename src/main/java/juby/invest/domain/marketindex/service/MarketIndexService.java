package juby.invest.domain.marketindex.service;

import juby.invest.domain.kis.market.dto.IndexPriceRes;
import juby.invest.domain.kis.market.service.MarketService;
import juby.invest.domain.kis.realtime.dto.RealtimeIndex;
import juby.invest.domain.kis.realtime.service.RealtimeIndexStore;
import juby.invest.domain.marketindex.converter.MarketIndexConverter;
import juby.invest.domain.marketindex.dto.MarketIndexDto;
import juby.invest.domain.marketindex.enums.MarketIndex;
import juby.invest.domain.marketindex.exception.MarketIndexException;
import juby.invest.domain.marketindex.exception.code.MarketIndexErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/***
 * 코스피·코스닥 지수를 조회한다.
 * <p>
 * 우선순위는 <b>실시간 -> 캐시된 REST -> REST 호출</b>이다.
 * 장중에는 웹소켓으로 받은 값이 쓰이고, 장외나 실시간 장애 시에는 KIS 현재지수 API로 내려간다.
 * 어느 쪽 값인지는 응답의 realtime 플래그로 알린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketIndexService {

    private final RealtimeIndexStore realtimeIndexStore;
    private final MarketService marketService;
    private final MarketIndexCache cache;

    /***
     * 지수 목록을 조회한다.
     * <p>
     * 지수 1건이 실패해도 나머지는 그대로 응답한다. 코스닥 조회가 막혔다고 코스피까지 화면에서 사라질 이유가 없다.
     * 전부 실패했을 때만 예외로 올린다.
     */
    public MarketIndexDto.MarketIndexRes getMarketIndices() {

        List<MarketIndexDto.Item> items = new ArrayList<>();

        for (MarketIndex marketIndex : Arrays.asList(MarketIndex.values())) {
            resolve(marketIndex).ifPresent(items::add);
        }

        if (items.isEmpty()){
            throw new MarketIndexException(MarketIndexErrorCode.INDEX_FETCH_FAILED);
        }

        return MarketIndexDto.MarketIndexRes.of(LocalDateTime.now(), items);
    }

    /*** 지수 1건을 구한다. 실패하면 비어 있는 값을 반환해 호출 쪽이 그 지수만 제외할 수 있게 한다. */
    private Optional<MarketIndexDto.Item> resolve(MarketIndex marketIndex) {

        Optional<RealtimeIndex> realtime = realtimeIndexStore.findFresh(marketIndex.getCode());
        if (realtime.isPresent()){
            return Optional.of(MarketIndexConverter.toItem(marketIndex, realtime.get().tick()));
        }

        Optional<IndexPriceRes.Info> cached = cache.find(marketIndex.getCode());
        if (cached.isPresent()){
            return Optional.of(MarketIndexConverter.toItem(marketIndex, cached.get()));
        }

        try {
            IndexPriceRes.Info fetched = marketService.getIndexPrice(marketIndex.getCode());
            cache.put(marketIndex.getCode(), fetched);

            return Optional.of(MarketIndexConverter.toItem(marketIndex, fetched));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("종료 신호를 받아 지수 조회를 중단합니다. 지수: {}", marketIndex.getLabel());
            return Optional.empty();

        } catch (Exception e) {
            // 지수 1건의 실패는 화면 전체를 막을 사유가 아니다. 전부 실패한 경우만 호출 쪽에서 예외가 된다.
            log.warn("지수 조회에 실패했습니다. 지수: {}", marketIndex.getLabel(), e);
            return Optional.empty();
        }
    }
}
