package juby.invest.domain.kis.realtime.converter;

import juby.invest.domain.kis.realtime.dto.RealtimeIndexTick;
import juby.invest.global.logging.ThrottledLogger;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/***
 * KIS 국내지수 실시간체결(H0UPCNT0) 프레임을 RealtimeIndexTick으로 바꾼다.
 * <p>
 * 프레임 분해는 {@link RealtimeFrameSplitter}가 하고, 여기서는 레코드 내 필드 의미만 다룬다.
 * <p>
 * <b>체결가(H0STCNT0)와 필드 자리가 다르다.</b> 전일대비율이 5번이 아니라 9번, 누적거래량이 13번이 아니라
 * 5번이다. 그래서 파서를 공유할 수 없고, 인덱스를 단위 테스트로 못 박아 둔다.
 */
public class RealtimeIndexParser {

    /** 국내지수 실시간체결 */
    public static final String TR_ID = "H0UPCNT0";

    /***
     * 업종코드 자리의 형식 검증용. KIS 업종코드는 4자리 숫자다(코스피 0001, 코스닥 1001).
     * 레코드 경계가 어긋나면 이 자리에 지수나 거래량이 들어오므로 밀린 레코드를 값싸게 걸러낼 수 있다.
     */
    private static final Pattern INDEX_CODE_FORMAT = Pattern.compile("[0-9]{4}");

    // 레코드 내 필드 인덱스 (KIS 공식 샘플 examples_llm/domestic_stock/index_ccnl의 컬럼 순서)
    private static final int INDEX_CODE = 0;
    private static final int BUSINESS_HOUR = 1;
    private static final int CURRENT_INDEX = 2;
    private static final int ACC_VOLUME = 5;
    private static final int ACC_TRADING_VALUE = 6;
    private static final int CHANGE_RATE = 9;
    private static final int OPEN_INDEX = 10;
    private static final int HIGH_INDEX = 11;
    private static final int LOW_INDEX = 12;

    /***
     * 우리가 읽는 마지막 인덱스 + 1. (지수 레코드는 총 30개 필드)
     * 길이를 30으로 못 박지 않는 이유는 체결가 파서와 같다. KIS가 뒤에 필드를 추가해도 깨지지 않아야 한다.
     */
    private static final int MIN_RECORD_SIZE = LOW_INDEX + 1;

    /*** 레코드 단위 실패는 수신마다 발생할 수 있으므로 60초에 1줄로 줄인다. */
    private static final ThrottledLogger log =
            new ThrottledLogger(RealtimeIndexParser.class, Duration.ofSeconds(60));

    /***
     * 평문 프레임을 파싱한다. 형식이 깨졌거나 다른 TR이면 빈 리스트를 반환한다.
     */
    public static List<RealtimeIndexTick> parse(String frame) {
        return RealtimeFrameSplitter.split(frame)
                .map(RealtimeIndexParser::parse)
                .orElseGet(List::of);
    }

    /***
     * 이미 분해된 프레임을 파싱한다. 지수 TR이 아니면 조용히 빈 리스트를 반환한다.
     */
    public static List<RealtimeIndexTick> parse(RealtimeFrame frame) {

        if (!TR_ID.equals(frame.trId())){
            return List.of();
        }

        if (frame.recordSize() < MIN_RECORD_SIZE){
            log.warn("실시간 지수 레코드가 너무 짧습니다. 레코드 {}개 필드, 최소 {}개 필요",
                    frame.recordSize(), MIN_RECORD_SIZE);
            return List.of();
        }

        List<RealtimeIndexTick> ticks = new ArrayList<>(frame.count());
        for (int record = 0; record < frame.count(); record++) {

            String indexCode = frame.field(record, INDEX_CODE);
            if (!INDEX_CODE_FORMAT.matcher(indexCode).matches()){
                log.warn("실시간 지수 레코드의 업종코드 자리가 업종코드 형식이 아닙니다. {}번째 레코드", record + 1);
                continue;
            }

            try {
                ticks.add(toTick(frame, record, indexCode));
            } catch (NumberFormatException e){
                // 레코드 1건이 깨져도 같은 프레임의 나머지는 살린다.
                log.warn("실시간 지수 레코드를 건너뜁니다. 업종코드: {}", indexCode);
            }
        }
        return ticks;
    }

    private static RealtimeIndexTick toTick(RealtimeFrame frame, int record, String indexCode) {
        return new RealtimeIndexTick(
                indexCode,
                frame.field(record, BUSINESS_HOUR),
                new BigDecimal(frame.field(record, CURRENT_INDEX)),
                new BigDecimal(frame.field(record, CHANGE_RATE)),
                new BigDecimal(frame.field(record, OPEN_INDEX)),
                new BigDecimal(frame.field(record, HIGH_INDEX)),
                new BigDecimal(frame.field(record, LOW_INDEX)),
                new BigDecimal(frame.field(record, ACC_VOLUME)),
                new BigDecimal(frame.field(record, ACC_TRADING_VALUE))
        );
    }
}
