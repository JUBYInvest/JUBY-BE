package juby.invest.domain.community.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostReqDto 검증")
class PostReqDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(Object dto) {
        Set<ConstraintViolation<Object>> violations = validator.validate(dto);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("제목 40자, 내용 1000자까지는 통과하고 한 글자라도 넘으면 막는다")
    void lengthLimits() {
        assertThat(invalidFields(new PostReqDto.CreateReq(null, "가".repeat(40), "가".repeat(1000)))).isEmpty();
        assertThat(invalidFields(new PostReqDto.CreateReq(null, "가".repeat(41), "가".repeat(1001))))
                .containsExactlyInAnyOrder("title", "content");
        assertThat(invalidFields(new PostReqDto.UpdateReq("가".repeat(41), "가".repeat(1001))))
                .containsExactlyInAnyOrder("title", "content");
    }

    @Test
    @DisplayName("종목 코드는 생략할 수 있지만, 보낸다면 6자리 숫자여야 한다")
    void optionalStockCode() {
        assertThat(invalidFields(new PostReqDto.CreateReq(null, "제목", "내용"))).isEmpty();
        assertThat(invalidFields(new PostReqDto.CreateReq("12345", "제목", "내용"))).containsExactly("stockCode");
    }
}
