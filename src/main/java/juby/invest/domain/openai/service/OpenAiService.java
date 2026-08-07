package juby.invest.domain.openai.service;

import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.entity.Personality;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.domain.openai.dto.OpenAiResDto;
import juby.invest.domain.pinecone.service.PineconeService;
import juby.invest.domain.stock.exception.StockException;
import juby.invest.domain.stock.exception.code.StockErrorCode;
import juby.invest.domain.stock.repository.StockRepository;
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
    private final StockRepository stockRepository;
    private final MemberRepository memberRepository;
    private final OpenAiChatModel chatModel;

    public OpenAiResDto.AskResult test() {

        String systemText = "너는 주식 초보자를 위한 비서야.";
        SystemMessage systemMessage = new SystemMessage(systemText);

        String userText = "삼성전자 전망에 대해 알려줘.";
        UserMessage userMessage = new UserMessage(userText);

        String result = chatModel.call(userMessage, systemMessage);
        return OpenAiResDto.AskResult.builder().answer(result).build();
    }

    public OpenAiResDto.AskResult askQuestion(Long memberId, String question, String stockName) throws ApiException {

        // 종목 존재 여부 확인 (백테스트 스코어 조회에 필요한 종목코드 확보)
        String stockCode = stockRepository.findByStockName(stockName)
                .orElseThrow(() -> new StockException(StockErrorCode.STOCK_NOT_FOUND))
                .getStockCode();

        // 로그인 사용자의 성향 테스트 결과로 백테스트 investType(1~5) 확보
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        Personality personality = member.getPersonality();
        if (personality == null) {
            throw new MemberException(MemberErrorCode.PERSONALITY_NOT_FOUND);
        }
        int investType = personality.getInvestPersonality().ordinal() + 1;

        log.info("질문 대상 종목코드: {}, 투자성향(investType): {}", stockCode, investType);

        // 프롬프트
        String systemText = "너는 주식 초보자를 위한 비서야. 초보자들이 이해하기 쉽게끔 설명해줘.";
        SystemMessage systemMessage = new SystemMessage(systemText);

        // 관련 뉴스 검색 + 실제 질문을 함께 전달
        String userText = """
                [관련 뉴스]
                %s

                [질문]
                %s
                """.formatted(pineconeService.searchData(question, stockName), question);
        UserMessage userMessage = new UserMessage(userText);

        String result = chatModel.call(userMessage, systemMessage);
        return OpenAiResDto.AskResult.builder().answer(result).build();
    }
}
