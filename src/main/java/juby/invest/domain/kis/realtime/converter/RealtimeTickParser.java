package juby.invest.domain.kis.realtime.converter;

import juby.invest.domain.kis.realtime.dto.RealtimeTick;
import juby.invest.global.logging.ThrottledLogger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/***
 * KIS 실시간 체결가(H0STCNT0) 프레임을 RealtimeTick으로 바꾼다.
 * <p>
 * 프레임 분해와 형식 검증은 {@link RealtimeFrameSplitter}가 하고, 여기서는 <b>레코드 내 필드 의미</b>만 다룬다.
 * 외부 I/O가 없는 순수 함수로 둔다. 필드 인덱스 오프셋이 이 연동에서 가장 흔한 버그이고,
 * 웹소켓 핸들러에 섞어두면 테스트로 고정할 수 없다.
 */
public class RealtimeTickParser {

    /** 국내주식 실시간체결가 */
    public static final String TR_ID = "H0STCNT0";

    /***
     * 종목코드 자리의 형식 검증용. KRX 단축 종목코드는 6자리 숫자다.
     * 레코드 경계가 어긋나면 이 자리에 가격이나 거래량이 들어오므로, 밀린 레코드를 값싸게 걸러낼 수 있다.
     * (필드 수가 건수로 나누어떨어지기만 하면 길이 검사만으로는 밀림을 잡지 못한다)
     */
    private static final Pattern STOCK_CODE_FORMAT = Pattern.compile("[0-9]{6}");

    // 레코드 내 필드 인덱스 (KIS 공식 샘플 ws_domestic_stock.py의 필드 순서)
    private static final int STOCK_CODE = 0;
    private static final int CONTRACT_TIME = 1;
    private static final int CURRENT_PRICE = 2;
    private static final int CHANGE_RATE = 5;
    private static final int OPEN_PRICE = 7;
    private static final int HIGH_PRICE = 8;
    private static final int LOW_PRICE = 9;
    private static final int ACC_VOLUME = 13;
    private static final int ACC_TRADING_VALUE = 14;

    /***
     * 우리가 읽는 마지막 인덱스 + 1.
     * 레코드 길이를 46으로 못 박지 않는 이유: KIS가 레코드 뒤에 필드를 추가한 전례가 있다(46 -> 47).
     * 필요한 필드는 모두 앞쪽에 있으므로 이 최소치만 확인하면 필드 추가에 깨지지 않는다.
     */
    private static final int MIN_RECORD_SIZE = ACC_TRADING_VALUE + 1;

    /*** 레코드 단위 실패는 틱마다 발생할 수 있으므로 60초에 1줄로 줄인다. */
    private static final ThrottledLogger log =
            new ThrottledLogger(RealtimeTickParser.class, Duration.ofSeconds(60));

    /***
     * 평문 프레임을 파싱한다. 형식이 깨졌거나 다른 TR이면 빈 리스트를 반환한다.
     */
    public static List<RealtimeTick> parse(String frame) {
        return RealtimeFrameSplitter.split(frame)
                .map(RealtimeTickParser::parse)
                .orElseGet(List::of);
    }

    /***
     * 이미 분해된 프레임을 파싱한다. 체결가 TR이 아니면 조용히 빈 리스트를 반환한다(호가 등이 섞여 들어올 수 있다).
     */
    public static List<RealtimeTick> parse(RealtimeFrame frame) {

        if (!TR_ID.equals(frame.trId())){
            return List.of();
        }

        if (frame.recordSize() < MIN_RECORD_SIZE){
            log.warn("실시간 체결 레코드가 너무 짧습니다. 레코드 {}개 필드, 최소 {}개 필요",
                    frame.recordSize(), MIN_RECORD_SIZE);
            return List.of();
        }

        List<RealtimeTick> ticks = new ArrayList<>(frame.count());
        for (int record = 0; record < frame.count(); record++) {

            String stockCode = frame.field(record, STOCK_CODE);
            if (!STOCK_CODE_FORMAT.matcher(stockCode).matches()){
                log.warn("실시간 체결 레코드의 종목코드 자리가 종목코드 형식이 아닙니다. {}번째 레코드", record + 1);
                continue;
            }

            try {
                ticks.add(toTick(frame, record, stockCode));
            } catch (NumberFormatException e){
                // 레코드 1건이 깨져도 같은 프레임의 나머지 체결은 살린다.
                log.warn("실시간 체결 레코드를 건너뜁니다. 종목코드: {}", stockCode);
            }
        }
        return ticks;
    }

    private static RealtimeTick toTick(RealtimeFrame frame, int record, String stockCode) {
        return new RealtimeTick(
                stockCode,
                frame.field(record, CONTRACT_TIME),
                Integer.parseInt(frame.field(record, CURRENT_PRICE)),
                Double.parseDouble(frame.field(record, CHANGE_RATE)),
                Integer.parseInt(frame.field(record, OPEN_PRICE)),
                Integer.parseInt(frame.field(record, HIGH_PRICE)),
                Integer.parseInt(frame.field(record, LOW_PRICE)),
                Long.parseLong(frame.field(record, ACC_VOLUME)),
                Long.parseLong(frame.field(record, ACC_TRADING_VALUE))
        );
    }
}
