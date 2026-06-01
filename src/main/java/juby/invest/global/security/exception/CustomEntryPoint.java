package juby.invest.global.security.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseErrorCode;
import juby.invest.global.apiPayload.code.GeneralErrorCode;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 UNAUTHORIZED 예외 핸들러
 */
@Component
public class CustomEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {

        ObjectMapper objectMapper = new ObjectMapper();
        BaseErrorCode errorCode = GeneralErrorCode.UNAUTHORIZED;

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(errorCode.getStatus().value());

        ApiResponse<Object> errorResponse = ApiResponse.onFailure(errorCode, null);

        // A(무엇을)를 JSON으로 바꿔서, B(어디로)라는 통로에 바로 쏴라.
        // errorResponse 객체를 JSON으로 번역함과 동시에, 중간에 메모리를 낭비하지 않고
        // 프론트엔드로 가는 응답 통로(OutputStream)에 쏟아붓는다.
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
