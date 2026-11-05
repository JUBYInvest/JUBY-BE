package juby.invest.domain.community.repository;

// PostRepository.countByStock 결과 한 줄. 쿼리의 별칭(as stockCode 등)과 getter 이름이 맞아야 채워진다.
public interface StockPostCount {
    String getStockCode();
    String getStockName();
    long getPostCount();
}
