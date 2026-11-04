package juby.invest.domain.kis.realtime.converter;

import juby.invest.domain.kis.realtime.dto.RealtimeIndexTick;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실시간 지수 프레임 파싱 검증.
 * <p>
 * 지수(H0UPCNT0)는 체결가(H0STCNT0)와 필드 자리가 다르다. 전일대비율이 9번, 누적거래량이 5번이다.
 * 체결가 파서에서 인덱스를 그대로 복사하면 등락률 자리에 거래량이 들어오므로, 여기서 자리를 못으로 박는다.
 */
@DisplayName("실시간 지수 프레임 파서")
class RealtimeIndexParserTest {

    private static final int KIS_RECORD_SIZE = 30;

    /***
     * 지수 레코드 1건. 읽지 않는 자리는 "0"으로 채워, 인덱스가 밀리면 테스트가 깨지게 한다.
     */
    private static String record(String indexCode, String businessHour, String currentIndex,
                                 String accVolume, String accTradingValue, String changeRate,
                                 String openIndex, String highIndex, String lowIndex, int recordSize) {
        String[] fields = new String[recordSize];
        Arrays.fill(fields, "0");
        fields[0] = indexCode;
        fields[1] = businessHour;
        fields[2] = currentIndex;
        fields[5] = accVolume;
        fields[6] = accTradingValue;
        fields[9] = changeRate;
        fields[10] = openIndex;
        fields[11] = highIndex;
        fields[12] = lowIndex;
        return String.join("^", fields);
    }

    private static String kospi(int recordSize) {
        return record("0001", "133000", "2543.21", "412345678", "9876543210",
                "-0.37", "2550.10", "2561.44", "2538.07", recordSize);
    }

    private static String frame(int count, String... records) {
        return "0|H0UPCNT0|" + count + "|" + String.join("^", records);
    }

    @Test
    @DisplayName("지수 1건 프레임의 각 필드를 제 자리에서 읽는다")
    void parsesSingleIndex() {

        List<RealtimeIndexTick> ticks = RealtimeIndexParser.parse(frame(1, kospi(KIS_RECORD_SIZE)));

        assertThat(ticks).hasSize(1);
        RealtimeIndexTick tick = ticks.getFirst();
        assertThat(tick.indexCode()).isEqualTo("0001");
        assertThat(tick.businessHour()).isEqualTo("133000");
        assertThat(tick.currentIndex()).isEqualByComparingTo("2543.21");
        assertThat(tick.changeRate()).isEqualByComparingTo("-0.37");
        assertThat(tick.openIndex()).isEqualByComparingTo("2550.10");
        assertThat(tick.highIndex()).isEqualByComparingTo("2561.44");
        assertThat(tick.lowIndex()).isEqualByComparingTo("2538.07");
        assertThat(tick.accVolume()).isEqualByComparingTo("412345678");
        assertThat(tick.accTradingValue()).isEqualByComparingTo("9876543210");
    }

    @Test
    @DisplayName("소수 둘째 자리까지 그대로 보존한다")
    void keepsDecimalPrecision() {

        List<RealtimeIndexTick> ticks = RealtimeIndexParser.parse(frame(1, kospi(KIS_RECORD_SIZE)));

        // 정수로 받으면 2543이 되어 소수점이 날아간다.
        assertThat(ticks.getFirst().currentIndex()).isEqualTo(new BigDecimal("2543.21"));
    }

    @Test
    @DisplayName("한 프레임에 묶여 온 복수 지수를 건수만큼 분리한다")
    void parsesMultipleIndicesInOneFrame() {

        String kosdaq = record("1001", "133001", "812.45", "1234", "5678",
                "1.02", "805.00", "815.33", "804.10", KIS_RECORD_SIZE);

        List<RealtimeIndexTick> ticks = RealtimeIndexParser.parse(frame(2, kospi(KIS_RECORD_SIZE), kosdaq));

        assertThat(ticks).extracting(RealtimeIndexTick::indexCode).containsExactly("0001", "1001");
        assertThat(ticks.get(1).currentIndex()).isEqualByComparingTo("812.45");
    }

    @Test
    @DisplayName("레코드 뒤에 필드가 추가돼도 같은 값을 읽는다")
    void toleratesAppendedFields() {

        List<RealtimeIndexTick> thirty = RealtimeIndexParser.parse(frame(1, kospi(30)));
        List<RealtimeIndexTick> thirtyOne = RealtimeIndexParser.parse(frame(1, kospi(31)));

        assertThat(thirtyOne).isEqualTo(thirty);
        assertThat(thirtyOne).hasSize(1);
    }

    @Test
    @DisplayName("지수가 아닌 TR(체결가 등)은 조용히 무시한다")
    void ignoresOtherTrId() {

        String frame = "0|H0STCNT0|1|" + kospi(KIS_RECORD_SIZE);

        assertThat(RealtimeIndexParser.parse(frame)).isEmpty();
    }

    @Test
    @DisplayName("업종코드 자리가 4자리 숫자가 아니면 그 레코드를 버린다")
    void dropsRecordWithInvalidIndexCode() {

        // 레코드 경계가 밀리면 이 자리에 지수나 거래량이 들어온다.
        String misaligned = record("2543.21", "133000", "2543.21", "1", "1",
                "0.1", "1", "1", "1", KIS_RECORD_SIZE);

        assertThat(RealtimeIndexParser.parse(frame(1, misaligned))).isEmpty();
    }

    @Test
    @DisplayName("숫자가 깨진 레코드만 버리고 같은 프레임의 나머지는 살린다")
    void skipsOnlyBrokenRecord() {

        String broken = record("1001", "133001", "", "1", "1",
                "1.02", "1", "1", "1", KIS_RECORD_SIZE);

        List<RealtimeIndexTick> ticks = RealtimeIndexParser.parse(frame(2, broken, kospi(KIS_RECORD_SIZE)));

        assertThat(ticks).hasSize(1);
        assertThat(ticks.getFirst().indexCode()).isEqualTo("0001");
    }

    @Test
    @DisplayName("우리가 읽는 필드에 미치지 못하는 짧은 레코드는 버린다")
    void rejectsTooShortRecord() {

        assertThat(RealtimeIndexParser.parse("0|H0UPCNT0|1|0001^133000^2543.21")).isEmpty();
    }

    @Test
    @DisplayName("형식이 깨진 프레임이나 빈 값에 예외를 던지지 않는다")
    void returnsEmptyOnMalformedFrame() {

        assertThat(RealtimeIndexParser.parse((String) null)).isEmpty();
        assertThat(RealtimeIndexParser.parse("")).isEmpty();
        assertThat(RealtimeIndexParser.parse("0|H0UPCNT0")).isEmpty();
        assertThat(RealtimeIndexParser.parse("0|H0UPCNT0|x|" + kospi(KIS_RECORD_SIZE))).isEmpty();
    }
}
