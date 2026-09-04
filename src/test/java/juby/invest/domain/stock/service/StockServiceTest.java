package juby.invest.domain.stock.service;

import juby.invest.domain.stock.converter.StockConverter;
import juby.invest.domain.stock.entity.Stock;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StockService 등락률 계산")
class StockServiceTest {

    @DisplayName("전일 종가 대비 등락률을 소수 둘째 자리까지 반환한다")
    @ParameterizedTest(name = "종가 {0}원, 전일 종가 {1}원 -> {2}%")
    @CsvSource({
            //  종가,  전일종가,  기대 등락률
            " 70800,  70300,    0.71",   // 상승 (+0.711237...%)
            " 69000,  70000,   -1.43",   // 하락 (-1.428571...%)
            " 70000,  70000,    0.00",   // 보합
            " 13000,  10000,   30.00",   // 상한가
            "  7000,  10000,  -30.00",   // 하한가
            " 20000,  10000,  100.00",   // 100% 상승 (증자/액면분할 등 예외 상황)
            "    50,  10000,  -99.50",   // 극단적 하락
    })
    void calculateFluctuation(int closePrice, int prevClosePrice, double expected){

        double actual = StockConverter.calculateFluctuation(closePrice, prevClosePrice);

        assertThat(actual).isEqualTo(expected);
    }

    @Nested
    @DisplayName("전일 종가가 없는 경우")
    class WhenPrevCloseIsUnavailable {

        @Test
        @DisplayName("전일 종가가 null이면 0.0을 반환한다")
        void returnZeroWhenPrevCloseIsNull(){
            assertThat(StockConverter.calculateFluctuation(70000, null)).isZero();
        }

        @Test
        @DisplayName("전일 종가가 0이면 0으로 나누지 않고, 0.0을 반환한다")
        void returnZeroWhenPrevCloseIsZero(){
            assertThat(StockConverter.calculateFluctuation(80000, 0)).isZero();
        }
    }

    @Test
    @DisplayName("결과는 항상 소수 둘째 자리 이하이다")
    void alwaysHasAtMostTwoDecimals(){
        for (int closePrice = 7000; closePrice <= 13000; closePrice++){
            double actual = StockConverter.calculateFluctuation(closePrice, 10000);

            assertThat(BigDecimal.valueOf(actual).scale())
                    .as("종가 %d원의 등락률 %s", closePrice, actual)
                    .isLessThanOrEqualTo(2);
        }
    }
}