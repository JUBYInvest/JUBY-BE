package juby.invest.domain.kis.realtime.converter;

import juby.invest.global.logging.ThrottledLogger;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/***
 * KIS 실시간 평문 프레임을 {@link RealtimeFrame}으로 분해한다. TR 종류와 무관한 공통 단계다.
 * <p>
 * 프레임 형식: {@code 0|H0STCNT0|002|005930^093354^73100^...}
 * <ul>
 *     <li>[0] 암호화 여부, [1] tr_id, [2] <b>데이터 건수</b>, [3] '^' 구분 페이로드</li>
 * </ul>
 * 체결가(H0STCNT0)와 지수(H0UPCNT0)가 같은 형식을 쓰므로, 분해와 형식 검증을 여기 한 곳에만 둔다.
 * 레코드 내 필드 의미는 TR마다 다르므로 각 파서가 해석한다.
 */
public class RealtimeFrameSplitter {

    // 틱마다 호출되는 경로이므로 패턴을 미리 컴파일해 둔다.
    private static final Pattern FRAME_DELIMITER = Pattern.compile(Pattern.quote("|"));
    private static final Pattern FIELD_DELIMITER = Pattern.compile(Pattern.quote("^"));

    /***
     * 프레임 형식이 깨지면 모든 프레임이 연달아 실패한다. 초당 수백 줄이 쌓이지 않게 60초에 1줄로 줄인다.
     */
    private static final ThrottledLogger log =
            new ThrottledLogger(RealtimeFrameSplitter.class, Duration.ofSeconds(60));

    /*** 알 수 없는 형식의 값을 로그에 남길 때의 최대 길이. 페이로드 전체가 로그로 새어 나가는 것을 막는다. */
    private static final int LOG_VALUE_MAX_LENGTH = 40;

    /***
     * 프레임을 분해한다. 형식이 깨졌으면 비어 있는 값을 반환한다(예외를 던지지 않는다).
     * 수신 스레드에서 호출되므로, 프레임 1건 때문에 세션이 끊기면 그 세션이 담당한 종목 전체가 멈춘다.
     */
    public static Optional<RealtimeFrame> split(String frame) {

        if (frame == null || frame.isBlank()){
            return Optional.empty();
        }

        // 페이로드에도 '|'가 들어올 수 있으므로 앞 3개만 끊고 나머지는 통째로 둔다.
        String[] parts = FRAME_DELIMITER.split(frame, 4);
        if (parts.length < 4){
            log.warn("실시간 프레임 형식이 올바르지 않습니다. 구분자 개수: {}", parts.length);
            return Optional.empty();
        }

        int count;
        try {
            count = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e){
            log.warn("실시간 프레임의 데이터 건수를 읽을 수 없습니다. tr_id: {}, 값: {}", parts[1], truncate(parts[2]));
            return Optional.empty();
        }

        if (count <= 0){
            log.warn("실시간 프레임의 데이터 건수가 올바르지 않습니다. tr_id: {}, 건수: {}", parts[1], count);
            return Optional.empty();
        }

        // 빈 필드도 세어야 하므로 limit -1 (trailing empty 유지)
        String[] fields = FIELD_DELIMITER.split(parts[3], -1);

        if (fields.length % count != 0){
            log.warn("실시간 프레임의 필드 수가 건수로 나누어지지 않습니다. tr_id: {}, 필드 {}개, 건수 {}건",
                    parts[1], fields.length, count);
            return Optional.empty();
        }

        return Optional.of(new RealtimeFrame(parts[1], count, fields.length / count, Arrays.asList(fields)));
    }

    private static String truncate(String value) {

        if (value == null || value.length() <= LOG_VALUE_MAX_LENGTH){
            return value;
        }
        return value.substring(0, LOG_VALUE_MAX_LENGTH) + "...";
    }
}
