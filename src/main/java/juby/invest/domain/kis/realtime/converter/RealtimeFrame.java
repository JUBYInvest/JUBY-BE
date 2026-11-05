package juby.invest.domain.kis.realtime.converter;

import java.util.List;

/***
 * 분해가 끝난 KIS 실시간 평문 프레임.
 * <p>
 * 한 프레임에는 같은 TR의 레코드가 건수만큼 구분자 없이 이어 붙어 있다.
 * 레코드 길이는 고정값이 아니라 <b>전체 필드 수 / 건수</b>로 구한다
 * (KIS가 레코드 뒤에 필드를 추가한 전례가 있다: 46 -> 47).
 *
 * @param trId       프레임의 tr_id (H0STCNT0, H0UPCNT0 ...)
 * @param count      레코드 건수
 * @param recordSize 레코드 1건의 필드 수
 * @param fields     '^'로 쪼갠 전체 필드
 */
public record RealtimeFrame(
        String trId,
        int count,
        int recordSize,
        List<String> fields
) {
    /***
     * record번째 레코드의 index번째 필드를 공백 없이 반환한다.
     *
     * @param record 0부터 시작하는 레코드 순서
     * @param index  레코드 내 필드 인덱스
     */
    public String field(int record, int index) {
        return fields.get(record * recordSize + index).trim();
    }
}
