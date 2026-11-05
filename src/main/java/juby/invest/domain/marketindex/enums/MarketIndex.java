package juby.invest.domain.marketindex.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/***
 * 조회 대상 국내 시장 지수.
 * <p>
 * code는 KIS 업종코드로, 실시간 구독의 tr_key와 REST 조회의 FID_INPUT_ISCD에 같은 값이 쓰인다.
 * 지수를 추가하려면 상수 1줄만 더하면 되지만, 실시간 등록 건수를 1건씩 더 쓴다는 점을 기억해야 한다.
 * (세션당 41건 상한)
 */
@Getter
@RequiredArgsConstructor
public enum MarketIndex {

    KOSPI("0001", "코스피"),
    KOSDAQ("1001", "코스닥");

    private final String code;
    private final String label;
}
