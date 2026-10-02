package juby.invest.domain.openai.service;

import juby.invest.domain.backtest.exception.BacktestException;
import juby.invest.domain.backtest.exception.code.BacktestErrorCode;
import juby.invest.domain.backtest.service.BacktestPresetService;
import juby.invest.domain.chat.entity.ChatContent;
import juby.invest.domain.chat.entity.ChatSession;
import juby.invest.domain.chat.service.ChatService;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.entity.Personality;
import juby.invest.domain.member.enums.InvestPersonality;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.domain.openai.prompt.PromptLoader;
import juby.invest.domain.pinecone.service.PineconeService;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpenAiService 주가(<price>) 섹션 라우팅")
class OpenAiServicePriceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @Mock private PineconeService pineconeService;
    @Mock private StockRepository stockRepository;
    @Mock private DailyPriceRepository dailyPriceRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private BacktestPresetService backtestPresetService;
    @Mock private ChatService chatService;
    @Mock private OpenAiChatModel chatModel;

    private OpenAiService openAiService;
    private final Stock samsung = Stock.builder().stockCode("005930").stockName("삼성전자").build();

    @BeforeEach
    void setUp() {
        // 실제 프롬프트 파일(v2)을 읽어 조립된 프롬프트까지 검증한다.
        openAiService = new OpenAiService(pineconeService, stockRepository, dailyPriceRepository, memberRepository,
                backtestPresetService, chatService, chatModel, new PromptLoader());

        Personality personality = Personality.builder()
                .investPersonality(InvestPersonality.위험중립형).description("설명").build();
        Member member = Member.builder().id(MEMBER_ID).personality(personality).build();
        given(memberRepository.findActiveById(MEMBER_ID)).willReturn(Optional.of(member));

        ChatSession session = mock(ChatSession.class);
        given(session.getId()).willReturn(SESSION_ID);
        given(chatService.getOrCreateSession(anyLong(), any(), anyString())).willReturn(session);
        given(chatService.getRecentMessages(eq(SESSION_ID), anyInt())).willReturn(List.of());
        given(chatService.appendMessage(any(), any(), anyString())).willReturn(mock(ChatContent.class));

        given(stockRepository.findByStockName("삼성전자")).willReturn(Optional.of(samsung));
        given(dailyPriceRepository.findTop20ByStockOrderByDateDesc(samsung)).willReturn(List.of(
                DailyPrice.builder().stock(samsung).date(LocalDate.parse("2026-10-01"))
                        .openPrice(70300).highPrice(71000).lowPrice(69800).closePrice(70800).volume(100).build(),
                DailyPrice.builder().stock(samsung).date(LocalDate.parse("2026-09-30"))
                        .openPrice(69500).highPrice(72000).lowPrice(69000).closePrice(70300).volume(100).build()));
    }

    // 1차(분류) 호출은 classifyJson을, 2차(답변) 호출은 고정 답변을 돌려주고, 2차 호출의 마지막 사용자 메시지를 반환한다.
    private String askAndCaptureAnswerPrompt(String classifyJson) throws Exception {
        given(chatModel.call(any(Prompt.class))).willReturn(response(classifyJson), response("답변"));

        openAiService.askQuestion(MEMBER_ID, "삼성전자 지금 얼마야?", null, null);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());
        List<Message> messages = captor.getAllValues().get(1).getInstructions();
        return messages.get(messages.size() - 1).getText();
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    @DisplayName("needsPrice가 true면 DB 일별 주가를 요약한 <price> 섹션이 들어간다")
    void includePriceSection() throws Exception {
        String userText = askAndCaptureAnswerPrompt(
                "{\"needsBacktest\": false, \"needsNews\": false, \"needsPrice\": true, \"stockName\": \"삼성전자\"}");

        assertThat(userText)
                .contains("<price>", "</price>")
                .contains("기준일: 2026-10-01")
                .contains("기준일 종가: 70,800원 (전일 대비 +0.71%)")
                .doesNotContain("<backtest>", "<news>");
    }

    @Test
    @DisplayName("needsPrice가 false면 <price> 섹션을 넣지 않고 주가도 조회하지 않는다")
    void excludePriceSection() throws Exception {
        String userText = askAndCaptureAnswerPrompt(
                "{\"needsBacktest\": false, \"needsNews\": false, \"needsPrice\": false, \"stockName\": \"삼성전자\"}");

        assertThat(userText).doesNotContain("<price>");
        verify(dailyPriceRepository, never()).findTop20ByStockOrderByDateDesc(any());
    }

    @Test
    @DisplayName("일별 주가 데이터가 없으면 <price> 섹션에 안내 문구가 들어간다")
    void noDailyPrices() throws Exception {
        given(dailyPriceRepository.findTop20ByStockOrderByDateDesc(samsung)).willReturn(List.of());

        String userText = askAndCaptureAnswerPrompt(
                "{\"needsBacktest\": false, \"needsNews\": false, \"needsPrice\": true, \"stockName\": \"삼성전자\"}");

        assertThat(userText).contains("해당 종목의 일별 주가 데이터가 아직 준비되지 않았습니다.");
    }

    @Test
    @DisplayName("분류 결과를 파싱하지 못하면 기본값으로 주가도 사용한다 (종목은 명시 파라미터로 전달)")
    void fallbackIncludesPrice() throws Exception {
        given(chatModel.call(any(Prompt.class))).willReturn(response("JSON 아님"), response("답변"));
        given(pineconeService.searchData(anyString(), anyString())).willReturn(List.of());
        given(backtestPresetService.getPreset(anyString(), anyInt(), any()))
                .willThrow(new BacktestException(BacktestErrorCode.PRESET_NOT_FOUND));

        openAiService.askQuestion(MEMBER_ID, "지금 얼마야?", "삼성전자", null);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());
        List<Message> messages = captor.getAllValues().get(1).getInstructions();
        assertThat(messages.get(messages.size() - 1).getText()).contains("<price>");
    }
}