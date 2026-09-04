package juby.invest.domain.member.service;

import juby.invest.domain.member.dto.LikeStockDto;
import juby.invest.domain.member.entity.LikeStock;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.enums.Role;
import juby.invest.domain.member.repository.LikeStockRepository;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.domain.stock.entity.Stock;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    private CustomOAuth2User user;
    private Member member;

    @BeforeEach
    void setUp(){
        likeStockService = new LikeStockService(memberRepository, stockRepository, dailyPriceRepository, likeStockRepository);
        user = new CustomOAuth2User(1L, Role.USER, "테스터");

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
}