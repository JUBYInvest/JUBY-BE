package juby.invest.global.logging;

import juby.invest.global.scheduler.BacktestPresetScheduler;
import juby.invest.global.scheduler.DailyPriceScheduler;
import juby.invest.global.scheduler.DeleteExpiredATScheduler;
import juby.invest.global.scheduler.UpsertNewsToPineconeScheduler;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SchedulerLoggingAspect (스케줄러 MDC 태깅)")
class SchedulerLoggingAspectTest {

    private final SchedulerLoggingAspect aspect = new SchedulerLoggingAspect();

    private LogCapture logs;

    @BeforeEach
    void setUp() {
        logs = LogCapture.attachTo(SchedulerLoggingAspect.class);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        logs.detach();
        MDC.clear();
    }

    /*** 아스펙트에 실제로 선언된 포인트컷을 읽어온다. 표현식을 바꾸면 이 테스트가 깨진다. */
    private static AspectJExpressionPointcut declaredPointcut() throws NoSuchMethodException {
        Around around = SchedulerLoggingAspect.class
                .getDeclaredMethod("around", ProceedingJoinPoint.class)
                .getAnnotation(Around.class);

        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(around.value());
        return pointcut;
    }

    @SuppressWarnings("rawtypes")
    private static ProceedingJoinPoint joinPointOf(Class<?> declaringType) {
        Signature signature = mock(Signature.class);
        when(signature.getDeclaringType()).thenReturn((Class) declaringType);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        return joinPoint;
    }

    @Nested
    @DisplayName("포인트컷")
    class Pointcut {

        @Test
        @DisplayName("스케줄러 4종의 @Scheduled 메서드 전부에 걸린다")
        void matchesEveryScheduler() throws Exception {
            AspectJExpressionPointcut pointcut = declaredPointcut();

            assertThat(matches(pointcut, DailyPriceScheduler.class, "getDailyPrice")).isTrue();
            assertThat(matches(pointcut, BacktestPresetScheduler.class, "recalculatePresetResults")).isTrue();
            assertThat(matches(pointcut, DeleteExpiredATScheduler.class, "deleteExpiredAT")).isTrue();
            assertThat(matches(pointcut, UpsertNewsToPineconeScheduler.class, "upsertAllStockNews")).isTrue();
        }

        /***
         * 회귀 방지. 포인트컷을 @annotation(Scheduled)로 되돌리면 DailyPriceScheduler가 조용히 누락된다.
         *
         * @Scheduled는 @Repeatable(Schedules.class)이고 getDailyPrice()에는 2개(16:00·16:30 본 실행,
         * 17:00 보정 실행)가 붙어 있다. 2개 이상이면 컴파일러가 @Schedules 컨테이너 하나로 감싸므로
         * 메서드에 직접 붙은 애너테이션은 @Schedules뿐이고, AspectJ는 컨테이너를 풀어보지 않는다.
         * 하필 가장 중요한(100종목 KIS 호출, 미실행 감지 알람 대상) 스케줄러가 빠진다.
         */
        @Test
        @DisplayName("@annotation(Scheduled) 방식은 @Scheduled가 2개인 메서드를 놓친다")
        void annotationPointcutMissesRepeatedScheduled() throws Exception {
            AspectJExpressionPointcut annotationBased = new AspectJExpressionPointcut();
            annotationBased.setExpression("@annotation(org.springframework.scheduling.annotation.Scheduled)");

            // @Scheduled 1개 -> 걸린다
            assertThat(matches(annotationBased, BacktestPresetScheduler.class, "recalculatePresetResults")).isTrue();

            // @Scheduled 2개 -> 걸리지 않는다. 이것이 패키지 기반 execution()을 쓰는 이유다.
            assertThat(matches(annotationBased, DailyPriceScheduler.class, "getDailyPrice")).isFalse();
        }

        private boolean matches(AspectJExpressionPointcut pointcut, Class<?> type, String methodName) throws Exception {
            Method method = type.getMethod(methodName);
            return pointcut.matches(method, type);
        }
    }

    @Nested
    @DisplayName("MDC 태깅")
    class Tagging {

        @Test
        @DisplayName("성공하면 jobResult=SUCCESS와 실행 정보를 남긴다")
        void tagsSuccess() throws Throwable {
            ProceedingJoinPoint joinPoint = joinPointOf(BacktestPresetScheduler.class);
            when(joinPoint.proceed()).thenReturn("done");

            Object result = aspect.around(joinPoint);

            assertThat(result).isEqualTo("done");
            assertThat(logs.lastMdc())
                    .containsEntry("jobName", "BacktestPresetScheduler")
                    .containsEntry("jobResult", "SUCCESS")
                    .containsKeys("runId", "jobLatencyMs");
        }

        @Test
        @DisplayName("실패하면 jobResult=FAILURE를 남기고 예외를 삼키지 않는다")
        void tagsFailureAndRethrows() throws Throwable {
            ProceedingJoinPoint joinPoint = joinPointOf(DailyPriceScheduler.class);
            when(joinPoint.proceed()).thenThrow(new IllegalStateException("KIS 호출 실패"));

            assertThatThrownBy(() -> aspect.around(joinPoint))
                    .isInstanceOf(IllegalStateException.class);

            // 예외를 삼키면 스케줄러가 실패해도 아무도 모른다.
            assertThat(logs.lastMdc())
                    .containsEntry("jobName", "DailyPriceScheduler")
                    .containsEntry("jobResult", "FAILURE");
        }

        @Test
        @DisplayName("성공·실패 모두 MDC를 비운다")
        void clearsMdcEitherWay() throws Throwable {
            ProceedingJoinPoint ok = joinPointOf(BacktestPresetScheduler.class);
            when(ok.proceed()).thenReturn(null);

            aspect.around(ok);
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

            ProceedingJoinPoint failing = joinPointOf(DailyPriceScheduler.class);
            when(failing.proceed()).thenThrow(new IllegalStateException("boom"));

            assertThatThrownBy(() -> aspect.around(failing)).isInstanceOf(IllegalStateException.class);

            // 스케줄러 스레드도 풀에서 재사용되므로 남겨두면 다음 실행에 섞인다.
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        }

        @Test
        @DisplayName("실행마다 runId가 다르다")
        void generatesFreshRunId() throws Throwable {
            ProceedingJoinPoint first = joinPointOf(DeleteExpiredATScheduler.class);
            when(first.proceed()).thenReturn(null);
            aspect.around(first);
            String firstRunId = logs.lastMdc().get("runId");

            ProceedingJoinPoint second = joinPointOf(DeleteExpiredATScheduler.class);
            when(second.proceed()).thenReturn(null);
            aspect.around(second);
            String secondRunId = logs.lastMdc().get("runId");

            // 보정 실행(16:30/17:00)처럼 같은 jobName이 하루에 여러 번 돌기 때문에 실행 단위 식별자가 필요하다.
            assertThat(firstRunId).isNotBlank().isNotEqualTo(secondRunId);
        }
    }
}