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
import juby.invest.domain.openai.converter.PriceSummaryConverter;
import juby.invest.domain.openai.dto.OpenAiResDto;
import juby.invest.domain.openai.exception.OpenAiException;
import juby.invest.domain.openai.exception.code.OpenAiErrorCode;
import juby.invest.domain.openai.prompt.PromptLoader;
import juby.invest.domain.pinecone.dto.PineconeDto;
import juby.invest.domain.pinecone.service.PineconeService;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.db_data.client.ApiException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAiService {

    private static final BacktestPeriod DEFAULT_BACKTEST_PERIOD = BacktestPeriod.SIX_MONTHS;
    private static final double RECOMMEND_SCORE_THRESHOLD = 50.0; // finalScore(100점 만점) 기준 추천/비추천 컷라인
    private static final int RECENT_MESSAGE_LIMIT = 20; // OpenAI 문맥에 포함할 직전 대화 이력 최대 개수
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // resources/prompts/ 아래 프롬프트 파일. 내용을 바꿀 때는 파일명의 버전을 올려 어떤 프롬프트로 만든 답변인지 추적할 수 있게 한다.
    private static final String CHAT_SYSTEM_PROMPT = "chat-system.v2.txt";
    private static final String CHAT_USER_PROMPT = "chat-user.v1.txt";
    private static final String CLASSIFY_SYSTEM_PROMPT = "classify-system.v2.txt";
    private static final String CLASSIFY_USER_PROMPT = "classify-user.v1.txt";

    private static final double CLASSIFY_TEMPERATURE = 0.0; // 분류: 같은 질문에 항상 같은 판단이 나오도록
    private static final double ANSWER_TEMPERATURE = 0.4; // 답변: 자연스러운 설명은 유지하되 사실 정확도를 우선

    // 프롬프트에서 데이터 경계로 쓰는 태그. 외부 텍스트에 섞여 들어오면 제거한다.
    private static final Pattern DATA_TAG = Pattern.compile(
            "</?\\s*(today|investor_profile|stock|price|backtest|news|question)\\s*>", Pattern.CASE_INSENSITIVE);

    private final PineconeService pineconeService;
    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final MemberRepository memberRepository;
    private final BacktestPresetService backtestPresetService;
    private final ChatService chatService;
    private final OpenAiChatModel chatModel;
    private final PromptLoader promptLoader;

    public OpenAiResDto.AskResult askQuestion(
            Long memberId, String question, String stockName, Long chatSessionId) throws ApiException {

        // 로그인 사용자의 성향 테스트 결과로 백테스트 investType(1~5) 확보
        Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        Personality personality = member.getPersonality();
        if (personality == null) {
            throw new MemberException(MemberErrorCode.PERSONALITY_NOT_FOUND);
        }
        int investType = personality.getInvestPersonality().ordinal() + 1;

        // chatSessionId가 없으면 이번 질문으로 제목을 자동 생성해 새 대화방을 만들고, 있으면 본인 소유 대화방인지 검증한다.
        ChatSession session = chatService.getOrCreateSession(memberId, chatSessionId, question);

        // 같은 대화방에서 이미 답변을 생성 중이면 409로 거절한다(질문 저장 전이라 거절된 질문은 남지 않는다).
        // 답변 처리가 성공하든 실패하든 끝나면 반드시 선점을 해제한다.
        chatService.acquireAnswering(session.getId());
        try {
            return answerInSession(session, personality, investType, question, stockName);
        } finally {
            releaseAnswering(session.getId());
        }
    }

    /***
     * 함수 기능: 답변 생성을 선점한 대화방에서 이전 대화 문맥을 참고해 이번 질문의 답변을 생성하고,
     *          질문/답변을 대화방에 저장한다.
     */
    private OpenAiResDto.AskResult answerInSession(
            ChatSession session, Personality personality, int investType,
            String question, String stockName) throws ApiException {

        // 이번 질문을 저장하기 "전" 시점의 직전 대화 이력. OpenAI 호출 실패와 무관하게 문맥 판단에만 쓴다.
        List<ChatContent> previousTurns = chatService.getRecentMessages(session.getId(), RECENT_MESSAGE_LIMIT);
        List<Message> historyMessages = toHistoryMessages(previousTurns);

        // 이전 대화가 없으면 이번이 첫 질문이므로, 빈 대화방으로 먼저 만들어져 제목이 기본값이면 질문으로 제목을 정한다.
        if (previousTurns.isEmpty()) {
            chatService.applyAutoTitleIfDefault(session.getId(), question);
        }

        // 사용자 질문은 이후 AI 호출이 실패하더라도 유지되도록 먼저 저장한다.
        chatService.appendMessage(session, ChatRole.USER, question);

        // 1차 AI 호출: 직전 대화 문맥을 참고해 답변에 필요한 데이터 종류 + 언급된(또는 지칭된) 종목명을 판단한다.
        RoutingDecision decision = classify(question, historyMessages);
        log.info("질문 라우팅 결과. needsBacktest: {}, needsNews: {}, needsPrice: {}, 추출된 종목명: {}",
                decision.needsBacktest(), decision.needsNews(), decision.needsPrice(), decision.stockName());

        // 명시적으로 넘어온 종목명(예: 종목 상세페이지에서 호출)을 우선하고, 없으면 문맥을 참고해 추출된 종목명을 쓴다.
        String candidateName = (stockName != null && !stockName.isBlank()) ? stockName : decision.stockName();

        Stock resolvedStock = null;
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
            resolvedStock = stock.get();
        }

        boolean hasStock = resolvedStock != null;
        boolean hasPrice = hasStock && decision.needsPrice();
        boolean hasBacktest = hasStock && decision.needsBacktest();
        boolean hasNews = hasStock && decision.needsNews();

        // 2차 AI 호출: 실제로 필요한 데이터 섹션만 태그로 감싸 이번 턴의 질문을 조립한다(불필요한 placeholder 없음).
        // 주가/백테스트/뉴스 데이터는 항상 "이번 질문" 기준으로 새로 조회하며, 과거 턴의 결과를 재사용하지 않는다.
        // 외부 텍스트(뉴스, 질문)는 태그를 흉내 내 데이터 경계를 깨지 못하도록 우리 태그 이름을 제거한 뒤 넣는다.
        List<String> sections = new ArrayList<>();
        // 로그인 사용자라면 항상 알 수 있는 정보라 "내 투자유형이 뭐야?" 같은 질문에도 근거로 쓸 수 있도록 항상 포함한다.
        sections.add(tag("investor_profile",
                personality.getInvestPersonality() + " - " + stripDataTags(personality.getDescription())));
        if (hasStock) {
            // 질문이 "이 종목"/"그 종목" 같은 지시어여도 답변은 실제 종목명으로 하도록 명시해준다.
            sections.add(tag("stock", resolvedStock.getStockName()));
        }
        if (hasPrice) {
            sections.add(tag("price", PriceSummaryConverter.toSummary(
                    dailyPriceRepository.findTop20ByStockOrderByDateDesc(resolvedStock))));
        }
        if (hasBacktest) {
            sections.add(tag("backtest", buildBacktestSummary(resolvedStock.getStockCode(), investType)));
        }
        if (hasNews) {
            sections.add(tag("news", stripDataTags(formatNews(pineconeService.searchData(question, candidateName)))));
        }
        String userText = promptLoader.render(CHAT_USER_PROMPT, Map.of(
                "today", LocalDate.now(KST).toString(),
                "context", String.join("\n\n", sections),
                "question", stripDataTags(question)));

        // 시스템 프롬프트는 데이터 조합과 무관하게 항상 같은 텍스트다(조건별 규칙은 "태그가 있으면" 형태로 파일에 포함).
        // 매 호출의 앞부분이 동일해야 OpenAI의 자동 프롬프트 캐싱이 적용된다.
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(promptLoader.load(CHAT_SYSTEM_PROMPT)));
        messages.addAll(historyMessages);
        messages.add(new UserMessage(userText));

        // 타임아웃, 429(요청 한도 초과) 등 OpenAI 호출 자체가 실패하면 원본 예외 메시지 대신 정해진 에러 코드로 응답한다.
        // 사용자 질문은 이미 저장돼 있으므로 대화방에는 질문만 남고, 재질문 시 이어서 대화할 수 있다.
        String result;
        try {
            result = callChatModel(messages, ANSWER_TEMPERATURE);
        } catch (RuntimeException e) {
            log.error("OpenAI 답변 생성 호출 실패. chatSessionId: {}", session.getId(), e);
            throw new OpenAiException(OpenAiErrorCode.CALL_FAILED);
        }
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

    // 선점 해제 실패가 원래 결과(답변 또는 예외)를 덮어쓰지 않도록 로그만 남긴다. 해제되지 않은 선점은 일정 시간 후 자동으로 풀린다.
    private void releaseAnswering(Long chatSessionId) {
        try {
            chatService.releaseAnswering(chatSessionId);
        } catch (RuntimeException e) {
            log.error("답변 생성 선점 해제 실패. chatSessionId: {}", chatSessionId, e);
        }
    }

    // 저장된 대화 이력(ChatContent)을 OpenAI 메시지 목록(User/Assistant)으로 변환한다.
    private List<Message> toHistoryMessages(List<ChatContent> previousTurns) {
        return previousTurns.stream()
                .<Message>map(c -> c.getRole() == ChatRole.USER
                        ? new UserMessage(c.getContent())
                        : new AssistantMessage(c.getContent()))
                .toList();
    }

    // 호출별 옵션(temperature)을 지정해 OpenAI를 호출하고 응답 텍스트를 꺼낸다. 모델명 등 나머지 옵션은 기본 설정을 따른다.
    private String callChatModel(List<Message> messages, double temperature) {
        Prompt prompt = new Prompt(messages, OpenAiChatOptions.builder().temperature(temperature).build());
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    // 프롬프트의 데이터 섹션을 XML 태그로 감싼다.
    private String tag(String name, String body) {
        return "<%s>\n%s\n</%s>".formatted(name, body, name);
    }

    // 외부 텍스트에 섞인 우리 데이터 태그(<news>, </question> 등)를 제거해, 태그를 흉내 내 데이터 경계를 깨지 못하게 한다.
    private String stripDataTags(String text) {
        return text == null ? "" : DATA_TAG.matcher(text).replaceAll("");
    }

    /***
     * 함수 기능: 사용자 질문을 분석해 답변 생성 시 주가/백테스트/뉴스 데이터가 필요한지, 질문에 특정 종목이
     *          언급(또는 대명사로 지칭)됐는지를 판단한다. 분류 호출이 실패하거나 파싱에 실패하면 안전하게
     *          모두 사용하는 것으로 대체한다.
     * @param historyMessages 직전 대화 이력. "그 종목은?" 같은 지시어가 가리키는 대상을 해석하는 데 참고한다.
     */
    private RoutingDecision classify(String question, List<Message> historyMessages) {

        BeanOutputConverter<RoutingDecision> converter = new BeanOutputConverter<>(RoutingDecision.class);

        // 지시문/판단 기준/few-shot 예시는 시스템 프롬프트에, 분류할 질문과 출력 형식은 사용자 메시지에 둔다.
        String userText = promptLoader.render(CLASSIFY_USER_PROMPT, Map.of(
                "question", stripDataTags(question),
                "format", converter.getFormat()));

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(promptLoader.load(CLASSIFY_SYSTEM_PROMPT)));
        messages.addAll(historyMessages);
        messages.add(new UserMessage(userText));

        try {
            // 분류는 같은 질문에 항상 같은 판단이 나와야 하므로 temperature를 0으로 고정한다.
            String raw = callChatModel(messages, CLASSIFY_TEMPERATURE);
            return converter.convert(raw);
        } catch (Exception e) {
            log.warn("질문 라우팅 분류 실패. 기본값(주가+백테스트+뉴스 모두 사용, 종목명 없음)으로 대체합니다.", e);
            return new RoutingDecision(true, true, true, null);
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

            // 수익률/MDD/변동성은 비율(0.123 = 12.3%)로 저장돼 있으므로 퍼센트로 환산해서 넘긴다.
            return """
                    (최근 %s, 투자성향 %d유형 기준)
                    - 종합 적합도 점수: %.1f점 (100점 만점, %.0f점 이상이면 추천)
                    - 투자성향 기준 추천 여부: %s
                    - 총수익률 %s, 연환산 수익률 %s
                    - 최대낙폭(MDD) %s, 변동성 %s
                    - 샤프지수 %s, 소르티노지수 %s
                    """.formatted(
                    preset.period().getLabel(), investType, r.finalScore(), RECOMMEND_SCORE_THRESHOLD, recommendation,
                    toPercent(r.profit().totalReturn()), toPercent(r.profit().annualReturn()),
                    toPercent(r.stable().mdd()), toPercent(r.stable().volatility()),
                    toDecimal(r.effect().sharpeRatio()), toDecimal(r.effect().sortinoRatio()));
        } catch (BacktestException e) {
            log.warn("백테스트 프리셋 없음. stockCode: {}, investType: {}", stockCode, investType, e);
            return "해당 종목의 백테스트 데이터가 아직 준비되지 않았습니다.";
        }
    }

    // 비율 값(0.123)을 소수 둘째 자리 퍼센트 문자열("12.30%")로 변환한다.
    private String toPercent(BigDecimal ratio) {
        if (ratio == null) {
            return "정보 없음";
        }
        return ratio.movePointRight(2).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    // 샤프/소르티노 같은 지수 값을 소수 둘째 자리까지 표시한다.
    private String toDecimal(BigDecimal value) {
        if (value == null) {
            return "정보 없음";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
    private record RoutingDecision(boolean needsBacktest, boolean needsNews, boolean needsPrice, String stockName) {}
}