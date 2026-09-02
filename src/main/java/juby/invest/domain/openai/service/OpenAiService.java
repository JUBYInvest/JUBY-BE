package juby.invest.domain.openai.service;

import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.enums.BacktestPeriod;
import juby.invest.domain.backtest.exception.BacktestException;
import juby.invest.domain.backtest.service.BacktestPresetService;
import juby.invest.domain.chat.entity.ChatContent;
import juby.invest.domain.chat.entity.ChatSession;
import juby.invest.domain.chat.enums.ChatRole;
import juby.invest.domain.chat.service.ChatService;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.entity.Personality;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.domain.openai.dto.OpenAiResDto;
import juby.invest.domain.openai.exception.OpenAiException;
import juby.invest.domain.openai.exception.code.OpenAiErrorCode;
import juby.invest.domain.pinecone.dto.PineconeDto;
import juby.invest.domain.pinecone.service.PineconeService;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.db_data.client.ApiException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAiService {

    private static final BacktestPeriod DEFAULT_BACKTEST_PERIOD = BacktestPeriod.SIX_MONTHS;
    private static final double RECOMMEND_SCORE_THRESHOLD = 50.0; // finalScore(100점 만점) 기준 추천/비추천 컷라인
    private static final int RECENT_MESSAGE_LIMIT = 20; // OpenAI 문맥에 포함할 직전 대화 이력 최대 개수

    private final PineconeService pineconeService;
    private final StockRepository stockRepository;
    private final MemberRepository memberRepository;
    private final BacktestPresetService backtestPresetService;
    private final ChatService chatService;
    private final OpenAiChatModel chatModel;

    public OpenAiResDto.AskResult askQuestion(
            Long memberId, String question, String stockName, Long chatSessionId) throws ApiException {

        // 로그인 사용자의 성향 테스트 결과로 백테스트 investType(1~5) 확보
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        Personality personality = member.getPersonality();
        if (personality == null) {
            throw new MemberException(MemberErrorCode.PERSONALITY_NOT_FOUND);
        }
        int investType = personality.getInvestPersonality().ordinal() + 1;

        // chatSessionId가 없으면 이번 질문으로 제목을 자동 생성해 새 대화방을 만들고, 있으면 본인 소유 대화방인지 검증한다.
        ChatSession session = chatService.getOrCreateSession(memberId, chatSessionId, question);

        // 이번 질문을 저장하기 "전" 시점의 직전 대화 이력. OpenAI 호출 실패와 무관하게 문맥 판단에만 쓴다.
        List<ChatContent> previousTurns = chatService.getRecentMessages(session.getId(), RECENT_MESSAGE_LIMIT);
        List<Message> historyMessages = toHistoryMessages(previousTurns);

        // 사용자 질문은 이후 AI 호출이 실패하더라도 유지되도록 먼저 저장한다.
        chatService.appendMessage(session, ChatRole.USER, question);

        // 1차 AI 호출: 직전 대화 문맥을 참고해 답변에 필요한 데이터 종류 + 언급된(또는 지칭된) 종목명을 판단한다.
        RoutingDecision decision = classify(question, historyMessages);
        log.info("질문 라우팅 결과. needsBacktest: {}, needsNews: {}, 추출된 종목명: {}",
                decision.needsBacktest(), decision.needsNews(), decision.stockName());

        // 명시적으로 넘어온 종목명(예: 종목 상세페이지에서 호출)을 우선하고, 없으면 문맥을 참고해 추출된 종목명을 쓴다.
        String candidateName = (stockName != null && !stockName.isBlank()) ? stockName : decision.stockName();

        String stockCode = null;
        String resolvedStockName = null;
        if (candidateName != null && !candidateName.isBlank()) {
            Optional<Stock> stock = stockRepository.findByStockName(candidateName);
            if (stock.isEmpty()) {
                // DB에 없는 종목명이면 조용히 넘어가지 않고, 2차 AI 호출 없이 바로 명확하게 안내한다.
                log.info("지원하지 않는 종목명. candidateName: {}", candidateName);
                String notice = "'%s'은(는) 현재 지원하지 않는 종목입니다. 지원되는 종목명으로 다시 질문해주세요."
                        .formatted(candidateName);
                ChatContent assistantMessage = chatService.appendMessage(session, ChatRole.ASSISTANT, notice);
                return OpenAiResDto.AskResult.builder()
                        .answer(notice)
                        .chatSessionId(session.getId())
                        .messageId(assistantMessage.getId())
                        .build();
            }
            stockCode = stock.get().getStockCode();
            resolvedStockName = stock.get().getStockName();
        }

        boolean hasStock = stockCode != null;
        boolean hasBacktest = hasStock && decision.needsBacktest();
        boolean hasNews = hasStock && decision.needsNews();

        // 2차 AI 호출: 실제로 필요한 섹션만 채워서 이번 턴의 질문을 조립한다(불필요한 placeholder 없음).
        // 백테스트/뉴스 데이터는 항상 "이번 질문" 기준으로 새로 조회하며, 과거 턴의 결과를 재사용하지 않는다.
        List<String> sections = new ArrayList<>();
        // 로그인 사용자라면 항상 알 수 있는 정보라 "내 투자유형이 뭐야?" 같은 질문에도 근거로 쓸 수 있도록 항상 포함한다.
        sections.add("[내 투자성향]\n" + personality.getInvestPersonality() + " - " + personality.getDescription());
        if (hasStock) {
            // 질문이 "이 종목"/"그 종목" 같은 지시어여도 답변은 실제 종목명으로 하도록 명시해준다.
            sections.add("[종목명]\n" + resolvedStockName);
        }
        if (hasBacktest) {
            sections.add("[백테스트 스코어]\n" + buildBacktestSummary(stockCode, investType));
        }
        if (hasNews) {
            sections.add("[관련 뉴스]\n" + formatNews(pineconeService.searchData(question, candidateName)));
        }
        sections.add("[질문]\n" + question);
        String userText = String.join("\n\n", sections);

        SystemMessage systemMessage = new SystemMessage(buildSystemPrompt(hasStock, hasBacktest, hasNews));
        List<Message> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.addAll(historyMessages);
        messages.add(new UserMessage(userText));

        String result = chatModel.call(messages.toArray(new Message[0]));
        if (result == null || result.isBlank()) {
            throw new OpenAiException(OpenAiErrorCode.EMPTY_ANSWER);
        }

        ChatContent assistantMessage = chatService.appendMessage(session, ChatRole.ASSISTANT, result);
        return OpenAiResDto.AskResult.builder()
                .answer(result)
                .chatSessionId(session.getId())
                .messageId(assistantMessage.getId())
                .build();
    }

    // 저장된 대화 이력(ChatContent)을 OpenAI 메시지 목록(User/Assistant)으로 변환한다.
    private List<Message> toHistoryMessages(List<ChatContent> previousTurns) {
        return previousTurns.stream()
                .<Message>map(c -> c.getRole() == ChatRole.USER
                        ? new UserMessage(c.getContent())
                        : new AssistantMessage(c.getContent()))
                .toList();
    }

    /***
     * 함수 기능: 실제로 답변에 쓰이는 데이터 조합(백테스트/뉴스 유무)에 맞춰 시스템 프롬프트를 조립한다.
     *          쓰지 않는 데이터에 대한 안내를 넣는 대신, 실제 쓰는 데이터에 맞는 지시만 포함시킨다.
     */
    private String buildSystemPrompt(boolean hasStock, boolean hasBacktest, boolean hasNews) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 주식 투자 초보자를 위한 비서야. 아래 원칙을 지켜서 답변해.\n");
        sb.append("- 주식과 이 시스템과 관련이 없는 질문이라면 투자와 관련한 질문만 해달라는 답변을 넘겨.\n");
        sb.append("- 초보자도 이해할 수 있는 쉬운 말로 설명해.\n");
        sb.append("- 답변 길이와 구성(형식)은 질문 성격에 맞게 그때그때 판단해. 종목 분석처럼 여러 근거(수치, 뉴스)를 " +
                "풀어서 설명해야 하는 질문이면 충분히 구체적으로 설명하고, \"내 투자유형이 뭐야\", \"PER이 뭐야\" 처럼 " +
                "단순한 사실 확인이나 개념 질문이면 군더더기 없이 물어본 것에 바로, 간결하게 답해. 짧게 답할 수 있는 " +
                "질문을 억지로 길게 늘리거나 불필요한 형식에 끼워 맞추지 마.\n");
        sb.append("- 대화 이력이 있다면 문맥 파악에는 참고하되, 답변의 근거 수치·뉴스는 반드시 이번 턴에 새로 주어진 " +
                "[백테스트 스코어]/[관련 뉴스] 섹션만 사용해. 이전 턴에서 언급됐던 수치나 기사를 이번 턴의 최신 정보인 것처럼 다시 인용하지 마.\n");

        if (hasStock) {
            sb.append("- 사용자가 \"이 종목\", \"그 종목\" 같은 지시어로 질문했더라도, 답변에서는 [종목명]에 주어진 " +
                    "실제 종목명을 사용해서 설명해. 지시어를 그대로 따라 쓰지 마.\n");
        }
        if (hasBacktest) {
            sb.append("- [백테스트 스코어]의 수치(수익률, MDD, 변동성, 샤프지수 등)를 반드시 근거로 인용하면서, ")
                    .append("각 수치가 무슨 의미인지 초보자 눈높이에서 해석까지 덧붙여 설명해.\n");
            sb.append("- [백테스트 스코어]에 있는 '투자성향 기준 추천 여부'를 답변에 명시적으로 언급하고, ")
                    .append("왜 그렇게 판단되는지(점수, 지표) 근거를 같이 설명해. 이 추천 여부 언급은 백테스트 데이터를 ")
                    .append("사용하는 답변에서만 하고, 백테스트 데이터가 없는 답변에는 넣지 마.\n");
        }
        if (hasNews) {
            sb.append("- [관련 뉴스]의 기사 내용을 반드시 근거로 인용하면서, 어떤 이슈이고 왜 중요한지 풀어서 설명해.\n");
        }
        if (!hasBacktest && !hasNews) {
            sb.append("- 지금은 특정 종목의 수치, 뉴스 데이터가 제공되지 않았어. 일반적인 투자 지식 범위에서만 답변하고, ")
                    .append("특정 수치나 최근 소식을 지어내지 마.\n");
        }
        if (hasBacktest || hasNews) {
            sb.append("- 수치, 뉴스를 종합해서 설명해야 하는 질문이면 가능하면 (1) 핵심 요약 (2) 근거 상세 설명 ")
                    .append("(3) 참고할 점/주의사항 순서로 구성해. 다만 질문이 그중 한 가지만 콕 집어 묻는 등 단순하면 ")
                    .append("이 형식에 얽매이지 말고 물어본 것에 바로 답해.\n");
        }
        sb.append("- 제공되지 않은 수치나 뉴스 내용을 지어내지 말고, 실제로 갖고 있는 정보 안에서만 답변해.\n");
        return sb.toString();
    }

    /***
     * 함수 기능: 사용자 질문을 분석해 답변 생성 시 백테스트/뉴스 데이터가 필요한지, 질문에 특정 종목이
     *          언급(또는 대명사로 지칭)됐는지를 판단한다. 분류 호출이 실패하거나 파싱에 실패하면 안전하게
     *          둘 다 사용하는 것으로 대체한다.
     * @param historyMessages 직전 대화 이력. "그 종목은?" 같은 지시어가 가리키는 대상을 해석하는 데 참고한다.
     */
    private RoutingDecision classify(String question, List<Message> historyMessages) {

        BeanOutputConverter<RoutingDecision> converter = new BeanOutputConverter<>(RoutingDecision.class);

        SystemMessage systemMessage = new SystemMessage(
                "너는 주식 투자 챗봇의 라우팅 어시스턴트야. 사용자 질문에 답하기 위해 어떤 데이터가 필요한지만 판단해. " +
                        "직전 대화 이력이 주어지면, \"그 종목은?\", \"그러면 뉴스는?\" 같이 이전 대화를 지칭하는 표현이 " +
                        "가리키는 대상을 그 이력을 참고해서 해석해.");

        String userText = """
                현재 질문: %s

                판단 기준:
                - needsBacktest: 수익률, 변동성, 적합도 등 정량적인 백테스트 데이터가 필요하면 true
                - needsNews: 최근 이슈, 실적, 사건 등 뉴스 맥락이 필요하면 true
                - 두 데이터 모두 필요 없는 일반적인 투자 개념 질문이면 둘 다 false
                - stockName: 현재 질문에서 특정 종목(예: "삼성전자")이 직접 언급되면 그 종목명을 적어줘.
                  직접 언급이 없어도 "그 종목", "그러면" 처럼 직전 대화를 지칭하는 표현이면, 대화 이력에서
                  가리키는 종목명을 찾아 적어줘. 그래도 특정 종목을 알 수 없거나 특정 종목에 대한 질문이
                  아니면 null로 남겨줘. 확실하지 않으면 추측해서 채우지 말고 null로 남겨줘.

                %s
                """.formatted(question, converter.getFormat());
        UserMessage userMessage = new UserMessage(userText);

        List<Message> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.addAll(historyMessages);
        messages.add(userMessage);

        try {
            String raw = chatModel.call(messages.toArray(new Message[0]));
            return converter.convert(raw);
        } catch (Exception e) {
            log.warn("질문 라우팅 분류 실패. 기본값(백테스트+뉴스 모두 사용, 종목명 없음)으로 대체합니다.", e);
            return new RoutingDecision(true, true, null);
        }
    }

    /***
     * 함수 기능: 종목코드/투자성향에 해당하는 백테스트 프리셋 결과를 사람이 읽기 좋은 텍스트로 변환한다.
     *          프리셋이 아직 계산되지 않은 종목이면 안내 문구로 대체한다.
     */
    private String buildBacktestSummary(String stockCode, int investType) {
        try {
            BacktestResDto.PresetResponse preset =
                    backtestPresetService.getPreset(stockCode, investType, DEFAULT_BACKTEST_PERIOD);
            BacktestResDto.QuantScoringResponse r = preset.result();

            String recommendation = r.finalScore() >= RECOMMEND_SCORE_THRESHOLD
                    ? "추천 (투자성향에 비교적 적합한 편)"
                    : "비추천 (투자성향에 비교적 적합하지 않은 편)";

            return """
                    (최근 %s, 투자성향 %d유형 기준)
                    - 종합 적합도 점수: %.1f점 (100점 만점, %.0f점 이상이면 추천)
                    - 투자성향 기준 추천 여부: %s
                    - 총수익률 %s%%, 연환산 수익률 %s%%
                    - 최대낙폭(MDD) %s%%, 변동성 %s%%
                    - 샤프지수 %s, 소르티노지수 %s
                    """.formatted(
                    preset.period().getLabel(), investType, r.finalScore(), RECOMMEND_SCORE_THRESHOLD, recommendation,
                    r.profit().totalReturn(), r.profit().annualReturn(),
                    r.stable().mdd(), r.stable().volatility(),
                    r.effect().sharpeRatio(), r.effect().sortinoRatio());
        } catch (BacktestException e) {
            log.warn("백테스트 프리셋 없음. stockCode: {}, investType: {}", stockCode, investType, e);
            return "해당 종목의 백테스트 데이터가 아직 준비되지 않았습니다.";
        }
    }

    // 뉴스 검색 결과를 프롬프트에 넣기 좋은 텍스트로 변환한다.
    private String formatNews(List<PineconeDto.StockNewsHit> hits) {
        if (hits.isEmpty()) {
            return "관련된 뉴스를 찾지 못했습니다.";
        }
        return hits.stream()
                .map(hit -> "- [%s] %s: %s".formatted(hit.pubDate(), hit.title(), hit.description()))
                .collect(Collectors.joining("\n"));
    }

    // AI 1차 호출(라우팅)의 판단 결과. 사용자에게는 노출되지 않는다.
    private record RoutingDecision(boolean needsBacktest, boolean needsNews, String stockName) {}
}