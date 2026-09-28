package juby.invest.global.apiPayload.code;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.beans.factory.config.BeanDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 도메인별 성공/에러 코드 enum의 code 문자열이 서로 겹치지 않는지 확인한다.
 * <p>
 * 프론트는 HTTP 상태가 아니라 이 code로 분기한다. 두 응답이 같은 code를 쓰면
 * 컴파일도 테스트도 통과하지만 프론트에서 둘을 구분할 방법이 사라진다.
 * (실제로 MemberSuccessCode의 투자유형 변경과 내 정보 수정이 MEMBER200_4를 공유하고 있었다.)
 */
@DisplayName("응답 코드 중복 검사")
class ApiCodeUniquenessTest {

    private static final String BASE_PACKAGE = "juby.invest";

    private List<Class<?>> scanEnums(Class<?> codeInterface) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(codeInterface));

        List<Class<?>> found = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                Class<?> clazz = Class.forName(definition.getBeanClassName());
                if (clazz.isEnum()) {
                    found.add(clazz);
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return found;
    }

    /** code 문자열 -> 그 코드를 쓰는 상수들(FQCN.상수명) */
    private Map<String, List<String>> collectCodes(List<Class<?>> enums) {
        Map<String, List<String>> byCode = new LinkedHashMap<>();

        for (Class<?> clazz : enums) {
            for (Object constant : clazz.getEnumConstants()) {
                String code = (constant instanceof BaseSuccessCode success)
                        ? success.getCode()
                        : ((BaseErrorCode) constant).getCode();

                byCode.computeIfAbsent(code, key -> new ArrayList<>())
                        .add(clazz.getSimpleName() + "." + constant);
            }
        }
        return byCode;
    }

    private void assertNoDuplicates(Class<?> codeInterface) {
        List<Class<?>> enums = scanEnums(codeInterface);

        // 스캔이 조용히 아무것도 못 찾으면 이 테스트는 항상 통과한다. 그 상태를 실패로 만든다.
        assertThat(enums)
                .as("%s 구현 enum을 찾지 못했다", codeInterface.getSimpleName())
                .isNotEmpty();

        Map<String, List<String>> duplicates = new LinkedHashMap<>(collectCodes(enums));
        duplicates.entrySet().removeIf(entry -> entry.getValue().size() == 1);

        assertThat(duplicates)
                .as("같은 code를 쓰는 상수가 있다")
                .isEmpty();
    }

    @Test
    @DisplayName("성공 코드가 중복되지 않는다")
    void successCodesAreUnique() {
        assertNoDuplicates(BaseSuccessCode.class);
    }

    @Test
    @DisplayName("에러 코드가 중복되지 않는다")
    void errorCodesAreUnique() {
        assertNoDuplicates(BaseErrorCode.class);
    }

    @Test
    @DisplayName("성공 코드와 에러 코드가 서로 겹치지 않는다")
    void successAndErrorCodesDoNotOverlap() {
        Set<String> successCodes = collectCodes(scanEnums(BaseSuccessCode.class)).keySet();
        Set<String> errorCodes = collectCodes(scanEnums(BaseErrorCode.class)).keySet();

        assertThat(successCodes).isNotEmpty();
        assertThat(errorCodes).isNotEmpty();
        assertThat(successCodes).doesNotContainAnyElementsOf(errorCodes);
    }
}
