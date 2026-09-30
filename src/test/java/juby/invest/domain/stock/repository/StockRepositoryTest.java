package juby.invest.domain.stock.repository;

import juby.invest.domain.stock.entity.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("StockRepository 종목명 검색")
class StockRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        em.persist(Stock.builder().stockCode("005930").stockName("삼성전자").build());
        em.persist(Stock.builder().stockCode("006400").stockName("삼성SDI").build());
        em.persist(Stock.builder().stockCode("000810").stockName("삼성화재").build());
        em.persist(Stock.builder().stockCode("999999").stockName("우리삼성").build());
        em.persist(Stock.builder().stockCode("000660").stockName("SK하이닉스").build());
        em.flush();
    }

    @Test
    @DisplayName("검색어가 포함된 종목을 검색어로 시작하는 종목 먼저, 그 다음 종목명 순으로 반환한다")
    void searchOrdersPrefixMatchFirst() {
        List<Stock> result = stockRepository.searchByStockName("삼성", PageRequest.of(0, 10));

        assertThat(result).extracting(Stock::getStockName)
                .containsExactly("삼성SDI", "삼성전자", "삼성화재", "우리삼성");
    }

    @Test
    @DisplayName("최대 개수만큼만 반환한다")
    void searchRespectsLimit() {
        List<Stock> result = stockRepository.searchByStockName("삼성", PageRequest.of(0, 2));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("이스케이프된 %는 와일드카드가 아닌 문자로 검색된다")
    void escapedWildcardIsLiteral() {
        List<Stock> result = stockRepository.searchByStockName("!%", PageRequest.of(0, 10));

        assertThat(result).isEmpty();
    }
}
