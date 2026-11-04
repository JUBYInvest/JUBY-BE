package juby.invest.domain.kis.realtime.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("실시간 프레임 분해")
class RealtimeFrameSplitterTest {

    /*** 필드가 size개인 레코드. 각 자리에 자기 인덱스를 넣어 어긋남을 바로 알 수 있게 한다. */
    private static String record(int size, int offset) {
        String[] fields = new String[size];
        for (int i = 0; i < size; i++) {
            fields[i] = String.valueOf(offset + i);
        }
        return String.join("^", fields);
    }

    @Test
    @DisplayName("tr_id와 건수를 읽고 레코드 길이를 필드 수에서 역산한다")
    void splitsFrame() {

        RealtimeFrame frame = RealtimeFrameSplitter
                .split("0|H0STCNT0|1|" + record(46, 0))
                .orElseThrow();

        assertThat(frame.trId()).isEqualTo("H0STCNT0");
        assertThat(frame.count()).isEqualTo(1);
        assertThat(frame.recordSize()).isEqualTo(46);
        assertThat(frame.field(0, 0)).isEqualTo("0");
        assertThat(frame.field(0, 45)).isEqualTo("45");
    }

    @Test
    @DisplayName("레코드 뒤에 필드가 추가돼도(46 -> 47) 그대로 분해한다")
    void toleratesAppendedFields() {

        RealtimeFrame frame = RealtimeFrameSplitter
                .split("0|H0STCNT0|2|" + record(47, 0) + "^" + record(47, 100))
                .orElseThrow();

        assertThat(frame.recordSize()).isEqualTo(47);
        assertThat(frame.count()).isEqualTo(2);
        // 두 번째 레코드의 첫 필드는 100부터 시작한다. 경계가 밀리면 여기서 깨진다.
        assertThat(frame.field(1, 0)).isEqualTo("100");
    }

    @Test
    @DisplayName("지수 프레임도 같은 형식이므로 같은 방식으로 분해된다")
    void splitsIndexFrame() {

        RealtimeFrame frame = RealtimeFrameSplitter
                .split("0|H0UPCNT0|1|" + record(30, 0))
                .orElseThrow();

        assertThat(frame.trId()).isEqualTo("H0UPCNT0");
        assertThat(frame.recordSize()).isEqualTo(30);
    }

    @Test
    @DisplayName("필드 수가 건수로 나누어지지 않으면 프레임을 버린다")
    void rejectsIndivisibleFieldCount() {

        assertThat(RealtimeFrameSplitter.split("0|H0STCNT0|3|" + record(46, 0))).isEmpty();
    }

    @Test
    @DisplayName("건수를 읽을 수 없거나 0 이하면 프레임을 버린다")
    void rejectsInvalidCount() {

        assertThat(RealtimeFrameSplitter.split("0|H0STCNT0|x|" + record(46, 0))).isEmpty();
        assertThat(RealtimeFrameSplitter.split("0|H0STCNT0|0|" + record(46, 0))).isEmpty();
        assertThat(RealtimeFrameSplitter.split("0|H0STCNT0|-1|" + record(46, 0))).isEmpty();
    }

    @Test
    @DisplayName("형식이 깨진 프레임이나 빈 값에 예외를 던지지 않는다")
    void returnsEmptyOnMalformedFrame() {

        assertThat(RealtimeFrameSplitter.split(null)).isEmpty();
        assertThat(RealtimeFrameSplitter.split("")).isEmpty();
        assertThat(RealtimeFrameSplitter.split("   ")).isEmpty();
        assertThat(RealtimeFrameSplitter.split("0|H0STCNT0")).isEmpty();
        assertThat(RealtimeFrameSplitter.split("{\"header\":{}}")).isEmpty();
    }

    @Test
    @DisplayName("페이로드에 '|'가 섞여 있어도 앞 3칸만 끊는다")
    void keepsPipeInsidePayload() {

        // 앞 3칸만 끊으므로 페이로드의 '|'는 필드 값으로 남는다.
        RealtimeFrame frame = RealtimeFrameSplitter
                .split("0|H0STCNT0|1|" + String.join("^", Arrays.asList("a|b", "c")))
                .orElseThrow();

        assertThat(frame.recordSize()).isEqualTo(2);
        assertThat(frame.field(0, 0)).isEqualTo("a|b");
    }
}
