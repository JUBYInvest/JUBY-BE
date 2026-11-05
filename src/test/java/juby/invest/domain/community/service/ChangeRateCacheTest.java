package juby.invest.domain.community.service;

import juby.invest.domain.kis.market.dto.CurrentPriceRes;
import juby.invest.domain.kis.market.exception.MarketException;
import juby.invest.domain.kis.market.exception.code.MarketErrorCode;
import juby.invest.domain.kis.market.service.MarketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeRateCache")
class ChangeRateCacheTest {

    @Mock private MarketService marketService;
    @InjectMocks private ChangeRateCache changeRateCache;

    private static CurrentPriceRes.Info price(String dayChange) {
        return CurrentPriceRes.Info.builder().dayChange(dayChange).build();
    }

    @Test
    @DisplayName("KIS의 등락률 문자열을 숫자로 바꿔 돌려주고, 같은 요청의 중복 종목은 한 번만 조회한다")
    void parsesAndDeduplicates() throws InterruptedException {
        given(marketService.getDailyPrice("005930")).willReturn(price("-5.05"));

        Map<String, ChangeRateCache.ChangeRate> rates = changeRateCache.get(List.of("005930", "005930"));

        assertThat(rates.get("005930").rate()).isEqualTo(-5.05);
        verify(marketService, times(1)).getDailyPrice("005930");
    }

    @Test
    @DisplayName("TTL 안에 다시 요청하면 KIS를 다시 부르지 않는다")
    void reusesWithinTtl() throws InterruptedException {
        given(marketService.getDailyPrice("005930")).willReturn(price("1.94"));

        changeRateCache.get(List.of("005930"));
        ChangeRateCache.ChangeRate second = changeRateCache.get(List.of("005930")).get("005930");

        assertThat(second.rate()).isEqualTo(1.94);
        verify(marketService, times(1)).getDailyPrice("005930");
    }

    @Test
    @DisplayName("KIS 조회가 실패하면 예외 대신 null을 돌려주고, 다른 종목은 정상 조회된다")
    void failureBecomesNull() throws InterruptedException {
        given(marketService.getDailyPrice("005930")).willThrow(new MarketException(MarketErrorCode.DAILY_PRICE_FAILED));
        given(marketService.getDailyPrice("000660")).willReturn(price("-6.73"));

        Map<String, ChangeRateCache.ChangeRate> rates = changeRateCache.get(List.of("005930", "000660"));

        assertThat(rates.get("005930").rate()).isNull();
        assertThat(rates.get("000660").rate()).isEqualTo(-6.73);
    }

    @Test
    @DisplayName("실패한 값은 정상 값보다 짧게만 유효하다")
    void failureHasShorterTtl() {
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 11, 5, 11, 30);
        ChangeRateCache.ChangeRate ok = new ChangeRateCache.ChangeRate(1.0, fetchedAt);
        ChangeRateCache.ChangeRate failed = new ChangeRateCache.ChangeRate(null, fetchedAt);
        LocalDateTime twoMinutesLater = fetchedAt.plusMinutes(2);

        assertThat(ok.isFresh(twoMinutesLater)).isTrue();
        assertThat(failed.isFresh(twoMinutesLater)).isFalse();
        assertThat(ok.isFresh(fetchedAt.plus(ChangeRateCache.TTL))).isFalse();
    }
}
