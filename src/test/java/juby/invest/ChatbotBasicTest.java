package juby.invest;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ChatbotBasicTest {

    @Autowired
    private OpenAiChatModel chatModel;

    @Test
    void test(){
        String systemText = "너는 주식 초보자를 위한 비서야.";
        SystemMessage systemMessage = new SystemMessage(systemText);

        String userText = "삼성전자 전망에 대해 알려줘.";
        UserMessage userMessage = new UserMessage(userText);

        String result = chatModel.call(userMessage, systemMessage);
        System.out.println(result);
    }
}
