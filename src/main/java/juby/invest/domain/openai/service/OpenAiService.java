package juby.invest.domain.openai.service;

import juby.invest.domain.pinecone.service.PineconeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.db_data.client.ApiException;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAiService {

    private final PineconeService pineconeService;
    private final OpenAiChatModel chatModel;

    public void test() {

        String systemText = "너는 주식 초보자를 위한 비서야.";
        SystemMessage systemMessage = new SystemMessage(systemText);

        String userText = "삼성전자 전망에 대해 알려줘.";
        UserMessage userMessage = new UserMessage(userText);

        String result = chatModel.call(userMessage, systemMessage);
        log.info(result);
    }

    public void askQuestion(String question, String stockName) throws ApiException {

        // 프롬프트
        String systemText = "너는 주식 초보자를 위한 비서야. 초보자들이 이해하기 쉽게끔 설명해줘.";
        SystemMessage systemMessage = new SystemMessage(systemText);

        // 찾은 질문
        UserMessage userMessage = new UserMessage(String.valueOf(pineconeService.searchData(question, stockName)));

        String result = chatModel.call(userMessage, systemMessage);
        log.info(result);
    }
}
