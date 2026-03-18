package juby.invest.initiate.loader;

import jakarta.transaction.Transactional;
import juby.invest.domain.Stock;
import juby.invest.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockLoader {

    private final StockRepository stockRepository;

    /***
     * 함수명: initStockCodeAndStockName()
     * 기능: 국내 시총 상위 100개 기업에 대해서 (종목코드, 종목번호)를 미리 DB에 INSERT 하기 위한 Initial 작업.
     */
    @Transactional
    public void initStockCodeAndStockName(){
        if (stockRepository.count() > 0){
            log.info("이미 국내 시총 100위 기업 저장 완료.");
            return;
        }

        List<Stock> initialStocks = List.of(
                Stock.builder().stockCode("005930").stockName("삼성전자").build(),
                Stock.builder().stockCode("000660").stockName("SK하이닉스").build(),
                Stock.builder().stockCode("005935").stockName("삼성전자우").build(),
                Stock.builder().stockCode("005380").stockName("현대차").build(),
                Stock.builder().stockCode("373220").stockName("LG에너지솔루션").build(),
                Stock.builder().stockCode("012450").stockName("한화에어로스페이스").build(),
                Stock.builder().stockCode("207940").stockName("삼성바이오로직스").build(),
                Stock.builder().stockCode("402340").stockName("SK스퀘어").build(),
                Stock.builder().stockCode("034020").stockName("두산에너빌리티").build(),
                Stock.builder().stockCode("000270").stockName("기아").build(),
                Stock.builder().stockCode("329180").stockName("HD현대중공업").build(),
                Stock.builder().stockCode("105560").stockName("KB금융").build(),
                Stock.builder().stockCode("068270").stockName("셀트리온").build(),
                Stock.builder().stockCode("028260").stockName("삼성물산").build(),
                Stock.builder().stockCode("055550").stockName("신한지주").build(),
                Stock.builder().stockCode("032830").stockName("삼성생명").build(),
                Stock.builder().stockCode("012330").stockName("현대모비스").build(),
                Stock.builder().stockCode("042660").stockName("한화오션").build(),
                Stock.builder().stockCode("006800").stockName("미래에셋증권").build(),
                Stock.builder().stockCode("035420").stockName("NAVER").build(),
                Stock.builder().stockCode("267260").stockName("HD현대일렉트릭").build(),
                Stock.builder().stockCode("010130").stockName("고려아연").build(),
                Stock.builder().stockCode("006400").stockName("삼성SDI").build(),
                Stock.builder().stockCode("042700").stockName("한미반도체").build(),
                Stock.builder().stockCode("086790").stockName("하나금융지주").build(),
                Stock.builder().stockCode("015760").stockName("한국전력").build(),
                Stock.builder().stockCode("009150").stockName("삼성전기").build(),
                Stock.builder().stockCode("272210").stockName("한화시스템").build(),
                Stock.builder().stockCode("009540").stockName("HD한국조선해양").build(),
                Stock.builder().stockCode("005490").stockName("POSCO홀딩스").build(),
                Stock.builder().stockCode("034730").stockName("SK").build(),
                Stock.builder().stockCode("010140").stockName("삼성중공업").build(),
                Stock.builder().stockCode("298040").stockName("효성중공업").build(),
                Stock.builder().stockCode("316140").stockName("우리금융지주").build(),
                Stock.builder().stockCode("035720").stockName("카카오").build(),
                Stock.builder().stockCode("064350").stockName("현대로템").build(),
                Stock.builder().stockCode("000810").stockName("삼성화재").build(),
                Stock.builder().stockCode("051910").stockName("LG화학").build(),
                Stock.builder().stockCode("010120").stockName("LS ELECTRIC").build(),
                Stock.builder().stockCode("267250").stockName("HD현대").build(),
                Stock.builder().stockCode("096770").stockName("SK이노베이션").build(),
                Stock.builder().stockCode("138040").stockName("메리츠금융지주").build(),
                Stock.builder().stockCode("011200").stockName("HMM").build(),
                Stock.builder().stockCode("066570").stockName("LG전자").build(),
                Stock.builder().stockCode("003670").stockName("포스코퓨처엠").build(),
                Stock.builder().stockCode("024110").stockName("기업은행").build(),
                Stock.builder().stockCode("033780").stockName("KT&G").build(),
                Stock.builder().stockCode("086280").stockName("현대글로비스").build(),
                Stock.builder().stockCode("069500").stockName("KODEX 200").build(),
                Stock.builder().stockCode("000150").stockName("두산").build(),
                Stock.builder().stockCode("047810").stockName("한국항공우주").build(),
                Stock.builder().stockCode("000720").stockName("현대건설").build(),
                Stock.builder().stockCode("079550").stockName("LIG넥스원").build(),
                Stock.builder().stockCode("017670").stockName("SK텔레콤").build(),
                Stock.builder().stockCode("030200").stockName("KT").build(),
                Stock.builder().stockCode("352820").stockName("하이브").build(),
                Stock.builder().stockCode("360750").stockName("TIGER 미국S&P500").build(),
                Stock.builder().stockCode("003550").stockName("LG").build(),
                Stock.builder().stockCode("047050").stockName("포스코인터내셔널").build(),
                Stock.builder().stockCode("010950").stockName("S-Oil").build(),
                Stock.builder().stockCode("0126Z0").stockName("삼성에피스홀딩스").build(),
                Stock.builder().stockCode("005830").stockName("DB손해보험").build(),
                Stock.builder().stockCode("018260").stockName("삼성에스디에스").build(),
                Stock.builder().stockCode("071050").stockName("한국금융지주").build(),
                Stock.builder().stockCode("039490").stockName("키움증권").build(),
                Stock.builder().stockCode("323410").stockName("카카오뱅크").build(),
                Stock.builder().stockCode("278470").stockName("에이피알").build(),
                Stock.builder().stockCode("259960").stockName("크래프톤").build(),
                Stock.builder().stockCode("005940").stockName("NH투자증권").build(),
                Stock.builder().stockCode("307950").stockName("현대오토에버").build(),
                Stock.builder().stockCode("000880").stockName("한화").build(),
                Stock.builder().stockCode("005387").stockName("현대차2우B").build(),
                Stock.builder().stockCode("003490").stockName("대한항공").build(),
                Stock.builder().stockCode("009830").stockName("한화솔루션").build(),
                Stock.builder().stockCode("007660").stockName("이수페타시스").build(),
                Stock.builder().stockCode("016360").stockName("삼성증권").build(),
                Stock.builder().stockCode("180640").stockName("한진칼").build(),
                Stock.builder().stockCode("379800").stockName("KODEX 미국S&P500").build(),
                Stock.builder().stockCode("377300").stockName("카카오페이").build(),
                Stock.builder().stockCode("459580").stockName("KODEX CD금리액티브(합성)").build(),
                Stock.builder().stockCode("133690").stockName("TIGER 미국나스닥100").build(),
                Stock.builder().stockCode("000100").stockName("유한양행").build(),
                Stock.builder().stockCode("326030").stockName("SK바이오팜").build(),
                Stock.builder().stockCode("396500").stockName("TIGER 반도체TOP10").build(),
                Stock.builder().stockCode("003230").stockName("삼양식품").build(),
                Stock.builder().stockCode("006260").stockName("LS").build(),
                Stock.builder().stockCode("090430").stockName("아모레퍼시픽").build(),
                Stock.builder().stockCode("443060").stockName("HD현대마린솔루션").build(),
                Stock.builder().stockCode("161390").stockName("한국타이어앤테크놀로지").build(),
                Stock.builder().stockCode("229200").stockName("KODEX 코스닥150").build(),
                Stock.builder().stockCode("488770").stockName("KODEX 머니마켓액티브").build(),
                Stock.builder().stockCode("102110").stockName("TIGER 200").build(),
                Stock.builder().stockCode("029780").stockName("삼성카드").build(),
                Stock.builder().stockCode("128940").stockName("한미약품").build(),
                Stock.builder().stockCode("032640").stockName("LG유플러스").build(),
                Stock.builder().stockCode("267270").stockName("HD건설기계").build(),
                Stock.builder().stockCode("064400").stockName("LG씨엔에스").build(),
                Stock.builder().stockCode("005385").stockName("현대차우").build(),
                Stock.builder().stockCode("028050").stockName("삼성E&A").build(),
                Stock.builder().stockCode("052690").stockName("한전기술").build());

        stockRepository.saveAll(initialStocks);
        log.info("기본 주식 종목 {}개 INSERT 완료", initialStocks.size());
    }
}
