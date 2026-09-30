package juby.invest.domain.openai.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * 클래스 기능: resources/prompts/ 아래의 프롬프트 텍스트 파일을 읽어 {{변수}} 자리에 값을 채운다.
 *            프롬프트 파일에는 few-shot JSON 예시처럼 중괄호가 그대로 들어가므로, 한 겹 중괄호가 아닌
 *            {{변수}} 형식만 치환한다. 파일 내용은 처음 읽을 때 캐싱한다.
 */
@Component
public class PromptLoader {

    private static final String PROMPT_DIR = "prompts/";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    // 변수가 없는 프롬프트 파일 내용을 그대로 반환한다.
    public String load(String fileName) {
        return cache.computeIfAbsent(fileName, this::read);
    }

    /***
     * 함수 기능: 프롬프트 파일의 {{변수}}를 values 값으로 한 번에 치환한다.
     *          치환된 값 안에 다시 {{...}}가 있어도 재치환하지 않으며(사용자 질문에 섞인 경우 대비),
     *          values에 없는 변수가 파일에 있으면 오타로 보고 예외를 던진다.
     */
    public String render(String fileName, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(load(fileName));
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.get(key);
            if (value == null) {
                throw new IllegalArgumentException("프롬프트 변수 값이 없습니다. file: %s, key: %s".formatted(fileName, key));
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String read(String fileName) {
        ClassPathResource resource = new ClassPathResource(PROMPT_DIR + fileName);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일을 읽을 수 없습니다. file: " + fileName, e);
        }
    }
}
