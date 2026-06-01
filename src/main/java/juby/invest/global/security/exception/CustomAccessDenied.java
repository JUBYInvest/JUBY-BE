package juby.invest.global.security.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.code.GeneralErrorCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 403 FORBIDDEN 예외 핸들러
 */
@Component
public class CustomAccessDenied implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {

        ObjectMapper objectMapper = new ObjectMapper();
        BaseErrorCode errorCode = GeneralErrorCode.FORBIDDEN;

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(errorCode.getStatus().value());

        ApiResponse<Object> errorResponse = ApiResponse.onFailure(errorCode, null);

        // Object를 JSON 형태로 변환.
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
