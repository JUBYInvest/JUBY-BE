package juby.invest.domain.member.service;

import juby.invest.domain.kis.realtime.dto.RealtimeTick;
import juby.invest.domain.kis.realtime.service.RealtimePriceStore;
import juby.invest.domain.member.dto.LikeStockDto;
import juby.invest.domain.member.dto.LikeStockListDto;
import juby.invest.domain.member.entity.LikeStock;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.enums.Role;
import juby.invest.domain.member.repository.LikeStockRepository;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.exception.StockException;
import juby.invest.domain.stock.exception.code.StockErrorCode;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import juby.invest.global.security.entity.CustomOAuth2User;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("관심 종목 서비스 테스트")
class LikeStockServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private StockRepository stockRepository;
    @Mock private DailyPriceRepository dailyPriceRepository;
    @Mock private LikeStockRepository likeStockRepository;

    private LikeStockService likeStockService;
    private RealtimePriceStore realtimePriceStore;
    private CustomOAuth2User user;
    private Member member;

    @BeforeEach
    void setUp(){
        realtimePriceStore = new RealtimePriceStore();
        likeStockService = new LikeStockService(memberRepository, stockRepository, dailyPriceRepository, likeStockRepository, realtimePriceStore);
        user = new CustomOAuth2User(1L, Role.USER, "테스터", null, null);

        member = Member.builder().id(1L).build();
    }

    @Nested
    @DisplayName("관심 종목 등록")
    class AddLikeStock {

        private final LikeStockDto.LikeStockReq req = new LikeStockDto.LikeStockReq("005930");

        @Test
        @DisplayName("관심 종목을 등록하면 liked=true로 응답한다")
        void 관심종목등록(){

            // given
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(stockRepository.findById("005930")).willReturn(Optional.of(new Stock("005930", "삼성전자")));

            // when
            LikeStockDto.LikeStockRes likeStockRes = likeStockService.addLikeStock(user, req);

            // then
            assertThat(likeStockRes.liked()).isTrue();
            then(likeStockRepository).should().save(any(LikeStock.class));
        }
    }

    @Nested
    @DisplayName("관심 종목 목록 조회")
    class GetLikeStockList {

        @Test
        @DisplayName("관심 종목은 있지만 daily_price가 비어 있으면 DAILYPRICE_NOT_FOUND 예외를 던진다")
        void throwsWhenDailyPriceIsEmpty() {

            // given
            Stock stock = new Stock("005930", "삼성전자");
            LikeStock likeStock = LikeStock.builder().member(member).stock(stock).build();
            given(likeStockRepository.findAllByMemberIdWithStock(1L)).willReturn(List.of(likeStock));
            given(dailyPriceRepository.findMaxDate()).willReturn(null);

            // when & then
            assertThatThrownBy(() -> likeStockService.getLikeStockList(user))
                    .isInstanceOf(StockException.class)
                    .hasFieldOrPropertyWithValue("errorCode", StockErrorCode.DAILYPRICE_NOT_FOUND);

            then(dailyPriceRepository).should(org.mockito.Mockito.never())
                    .findMaxDateBefore(any());
        }

        @Test
        @DisplayName("장중 실시간 체결가가 있으면 일봉 종가 대신 현재가로 응답한다")
        void usesRealtimePriceWhenAvailable() {

            // given
            Stock stock = new Stock("005930", "삼성전자");
            LikeStock likeStock = LikeStock.builder().member(member).stock(stock).build();
            given(likeStockRepository.findAllByMemberIdWithStock(1L)).willReturn(List.of(likeStock));
            given(dailyPriceRepository.findMaxDate()).willReturn(LocalDate.of(2026, 11, 3));
            given(dailyPriceRepository.findMaxDateBefore(any())).willReturn(LocalDate.of(2026, 10, 30));
            given(dailyPriceRepository.findAllByDateAndStockCodesWithStock(any(), any())).willReturn(List.of());

            realtimePriceStore.put(new RealtimeTick("005930", "133000", 73100, -0.14,
                    70000, 74000, 69000, 1234L, 3_000_000_000_000L));

            // when
            LikeStockListDto.LikeStockListRes response = likeStockService.getLikeStockList(user);

            // then
            // 일봉이 비어 있어도(기준일 일봉 조회 결과 없음) 실시간 값으로 가격을 채운다.
            assertThat(response.likeStockList()).hasSize(1);
            assertThat(response.likeStockList().getFirst().closePrice()).isEqualTo(73100);
            assertThat(response.likeStockList().getFirst().fluctuate()).isEqualTo(-0.14);
            assertThat(response.likeStockList().getFirst().realtime()).isTrue();
        }
    }
}