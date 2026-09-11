package juby.invest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAI 연동이 실제로 동작하는지 확인하는 수동 스모크 테스트.
 * <p>
 * 진짜 OpenAI 서버를 호출한다. 그래서 기본적으로는 건너뛴다.
 * 매 빌드마다 돌면 호출 비용이 나가고, 네트워크나 OpenAI 장애에 빌드가 끌려간다.
 * 응답 내용도 매번 달라 단정할 수 있는 것이 "호출이 성공했다" 정도뿐이다.
 * <p>
 * 실행하려면 환경변수 두 개가 필요하다.
 * <pre>
 * RUN_OPENAI_TEST=true OPEN_API_KEY=sk-... ./gradlew test --tests "*ChatbotBasicTest"
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_OPENAI_TEST", matches = "true",
        disabledReason = "OpenAI를 실제로 호출하는 수동 테스트다. RUN_OPENAI_TEST=true로 실행한다.")
@TestPropertySource(properties = "spring.ai.openai.api-key=${OPEN_API_KEY}")
@DisplayName("OpenAI 챗봇 연동 (수동)")
public class ChatbotBasicTest {

    @Autowired
    private OpenAiChatModel chatModel;

    @Test
    @DisplayName("질문을 보내면 응답을 받는다")
    void test(){
        String systemText = "너는 주식 초보자를 위한 비서야.";
        SystemMessage systemMessage = new SystemMessage(systemText);

        String userText = "삼성전자 전망에 대해 알려줘.";
        UserMessage userMessage = new UserMessage(userText);

        String result = chatModel.call(userMessage, systemMessage);

        System.out.println(result);
        // 내용은 매번 달라지므로 "응답이 돌아왔다"까지만 확인한다.
        assertThat(result).isNotBlank();
    }
}
