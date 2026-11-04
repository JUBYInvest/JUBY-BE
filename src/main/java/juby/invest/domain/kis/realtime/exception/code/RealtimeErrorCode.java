package juby.invest.domain.kis.realtime.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/***
 * 실시간 시세 수집 관련 에러 코드.
 * 사용자 요청 경로가 아니라 스케줄러/세션 관리에서 터지므로 응답으로 나가는 일은 거의 없지만,
 * 프로젝트 규약대로 코드를 부여해 로그 레벨(4xx=WARN, 5xx=ERROR)과 알림 기준을 일관되게 유지한다.
 */
@Getter
@RequiredArgsConstructor
public enum RealtimeErrorCode implements BaseErrorCode {

    // 설정 문제라 사람이 손대야 풀린다. 재시도로는 복구되지 않으므로 500으로 둔다.
    NO_CREDENTIAL_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "REALTIME500_1", "실시간 시세용 자격증명이 설정되지 않았습니다."),
    SUBSCRIPTION_LIMIT_EXCEEDED(HttpStatus.INTERNAL_SERVER_ERROR, "REALTIME500_2", "구독할 종목 수가 실시간 등록 상한을 초과했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
