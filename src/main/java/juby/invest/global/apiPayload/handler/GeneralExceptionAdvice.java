package juby.invest.global.apiPayload.handler;

import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.code.GeneralErrorCode;
import juby.invest.global.apiPayload.exception.ProjectException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GeneralExceptionAdvice {

    // 프로젝트 예외 처리
    @ExceptionHandler(ProjectException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectException(ProjectException e){
        BaseErrorCode errorCode = e.getErrorCode();
        log.error("[ProjectException] code={}, message={}", errorCode.getCode(), errorCode.getMessage(), e);
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.onFailure(errorCode, null));
    }

    // @Valid 검증 실패 예외 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleNotValidException(MethodArgumentNotValidException e){

        // 검증 실패한 변수명과 실패 이유를 담을 Map
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach((error) -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        BaseErrorCode errorCode = GeneralErrorCode.BAD_REQUEST;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.onFailure(errorCode, errors));
    }

    // enum 타입 파라미터 바인딩 예외 처리
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<String>> handleTypeMismatchException(MethodArgumentTypeMismatchException e){

        // 실패한 파라미터를 담을 String
        String detail = e.getName() + "파라미터 타입이 올바르지 않습니다.";
        BaseErrorCode errorCode = GeneralErrorCode.BAD_REQUEST;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.onFailure(errorCode, detail));
    }

    // 지정되지 않은 예외 처리
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<String>> handleGlobalException(RuntimeException e){

        // @PreAuthorize에 의한 AccessDenied 예외일 경우, ExceptionTranslation 필터에게 넘겨준다.
        if (e instanceof org.springframework.security.access.AccessDeniedException){
            throw e;
        }

        log.error("[UnhandledException] {}", e.getMessage(), e);

        BaseErrorCode errorCode = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.onFailure(errorCode, e.getMessage()));
    }
}