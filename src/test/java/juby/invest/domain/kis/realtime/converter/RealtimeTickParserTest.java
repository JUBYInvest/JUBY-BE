package juby.invest.domain.kis.realtime.converter;

import juby.invest.domain.kis.realtime.dto.RealtimeTick;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실시간 체결 프레임 파싱 검증.
 * <p>
 * 이 연동에서 가장 흔한 버그는 필드 인덱스 오프셋이다. 어느 자리에서 무엇을 읽는지 여기서 못으로 박는다.
 * KIS가 레코드 뒤에 필드를 추가한 전례가 있어(46 -> 47), 길이가 늘어도 같은 결과가 나오는지도 함께 본다.
 */
@DisplayName("실시간 체결 프레임 파서")
class RealtimeTickParserTest {

    private static final int KIS_RECORD_SIZE = 46;

    /***
     * 레코드 1건을 만든다. 읽지 않는 자리는 "0"으로 채워, 인덱스가 밀리면 테스트가 깨지게 한다.
     * @param recordSize 레코드 전체 필드 수 (KIS가 뒤에 필드를 추가한 상황을 흉내내기 위해 가변)
     */
    private static String record(String stockCode, String contractTime, String currentPrice,
                                 String changeRate, String openPrice, String highPrice, String lowPrice,
                                 String accVolume, String accTradingValue, int recordSize) {
        String[] fields = new String[recordSize];
        java.util.Arrays.fill(fields, "0");
        fields[0] = stockCode;
        fields[1] = contractTime;
        fields[2] = currentPrice;
        fields[5] = changeRate;
        fields[7] = openPrice;
        fields[8] = highPrice;
        fields[9] = lowPrice;
        fields[13] = accVolume;
        fields[14] = accTradingValue;
        return String.join("^", fields);
    }

    private static String samsung(int recordSize) {
        return record("005930", "093354", "73100", "-0.14", "73500", "74000", "72800",
                "1234567", "90123456789", recordSize);
    }

    private static String frame(int count, String... records) {
        return "0|H0STCNT0|" + count + "|" + String.join("^", records);
    }

    @Test
    @DisplayName("체결 1건 프레임의 각 필드를 제 자리에서 읽는다")
    void parsesSingleTick() {
        List<RealtimeTick> ticks = RealtimeTickParser.parse(frame(1, samsung(KIS_RECORD_SIZE)));

        assertThat(ticks).hasSize(1);
        RealtimeTick tick = ticks.getFirst();
        assertThat(tick.stockCode()).isEqualTo("005930");
        assertThat(tick.contractTime()).isEqualTo("093354");
        assertThat(tick.currentPrice()).isEqualTo(73100);
        assertThat(tick.changeRate()).isEqualTo(-0.14);
        assertThat(tick.openPrice()).isEqualTo(73500);
        assertThat(tick.highPrice()).isEqualTo(74000);
        assertThat(tick.lowPrice()).isEqualTo(72800);
        assertThat(tick.accVolume()).isEqualTo(1_234_567L);
        assertThat(tick.accTradingValue()).isEqualTo(90_123_456_789L);
    }

    @Test
    @DisplayName("한 프레임에 묶여 온 복수 체결을 건수만큼 분리한다")
    void parsesMultipleTicksInOneFrame() {
        String kakao = record("035720", "093355", "41500", "3.20", "40000", "41800", "39900",
                "222", "333", KIS_RECORD_SIZE);

        List<RealtimeTick> ticks = RealtimeTickParser.parse(frame(2, samsung(KIS_RECORD_SIZE), kakao));

        assertThat(ticks).hasSize(2);
        assertThat(ticks).extracting(RealtimeTick::stockCode).containsExactly("005930", "035720");
        assertThat(ticks).extracting(RealtimeTick::currentPrice).containsExactly(73100, 41500);
    }

    @Test
    @DisplayName("레코드 뒤에 필드가 추가돼도(46 -> 47) 같은 값을 읽는다")
    void toleratesAppendedFields() {
        List<RealtimeTick> fortySix = RealtimeTickParser.parse(frame(1, samsung(46)));
        List<RealtimeTick> fortySeven = RealtimeTickParser.parse(frame(1, samsung(47)));

        assertThat(fortySeven).isEqualTo(fortySix);
        assertThat(fortySeven).hasSize(1);
    }

    @Test
    @DisplayName("체결가가 아닌 TR(호가 등)은 조용히 무시한다")
    void ignoresOtherTrId() {
        String frame = "0|H0STASP0|1|" + samsung(KIS_RECORD_SIZE);

        assertThat(RealtimeTickParser.parse(frame)).isEmpty();
    }

    @Test
    @DisplayName("숫자가 깨진 레코드만 버리고 같은 프레임의 나머지는 살린다")
    void skipsOnlyBrokenRecord() {
        String broken = record("035720", "093355", "", "3.20", "40000", "41800", "39900",
                "222", "333", KIS_RECORD_SIZE);

        List<RealtimeTick> ticks = RealtimeTickParser.parse(frame(2, broken, samsung(KIS_RECORD_SIZE)));

        assertThat(ticks).hasSize(1);
        assertThat(ticks.getFirst().stockCode()).isEqualTo("005930");
    }

    @Test
    @DisplayName("필드 수가 건수로 나누어지지 않으면 프레임 전체를 버린다")
    void rejectsFrameWithIndivisibleFieldCount() {
        // 46필드 레코드 1건인데 건수를 3건이라고 주장하는 프레임 (46 % 3 != 0)
        String frame = "0|H0STCNT0|3|" + samsung(KIS_RECORD_SIZE);

        assertThat(RealtimeTickParser.parse(frame)).isEmpty();
    }

    @Test
    @DisplayName("레코드 경계가 어긋난 프레임은 종목코드 형식 검사로 걸러낸다")
    void dropsMisalignedRecords() {
        // 46필드 레코드 1건인데 건수를 2건이라고 주장하면 레코드 길이가 23으로 잘못 계산된다.
        // 나누어떨어지므로 길이 검사만으로는 통과하는 경우다.
        String frame = "0|H0STCNT0|2|" + samsung(KIS_RECORD_SIZE);

        List<RealtimeTick> ticks = RealtimeTickParser.parse(frame);

        // 앞쪽 레코드는 실제 값과 자리가 겹쳐 살아남지만, 밀린 뒤쪽 레코드는 버려진다.
        assertThat(ticks).hasSize(1);
        assertThat(ticks.getFirst().stockCode()).isEqualTo("005930");
    }

    @Test
    @DisplayName("우리가 읽는 필드에 미치지 못하는 짧은 레코드는 버린다")
    void rejectsTooShortRecord() {
        String frame = "0|H0STCNT0|1|005930^093354^73100";

        assertThat(RealtimeTickParser.parse(frame)).isEmpty();
    }

    @Test
    @DisplayName("형식이 깨진 프레임이나 빈 값에 예외를 던지지 않는다")
    void returnsEmptyOnMalformedFrame() {
        assertThat(RealtimeTickParser.parse((String) null)).isEmpty();
        assertThat(RealtimeTickParser.parse("")).isEmpty();
        assertThat(RealtimeTickParser.parse("0|H0STCNT0")).isEmpty();
        assertThat(RealtimeTickParser.parse("0|H0STCNT0|x|" + samsung(KIS_RECORD_SIZE))).isEmpty();
        assertThat(RealtimeTickParser.parse("0|H0STCNT0|0|")).isEmpty();
    }
}
