package juby.invest.domain.openai.service;

import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.enums.BacktestPeriod;
import juby.invest.domain.backtest.exception.BacktestException;
import juby.invest.domain.backtest.service.BacktestPresetService;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.entity.Personality;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.domain.openai.dto.OpenAiResDto;
import juby.invest.domain.pinecone.dto.PineconeDto;
import juby.invest.domain.pinecone.service.PineconeService;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.db_data.client.ApiException;
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

    private final PineconeService pineconeService;
    private final StockRepository stockRepository;
    private final MemberRepository memberRepository;
    private final BacktestPresetService backtestPresetService;
    private final OpenAiChatModel chatModel;

    public OpenAiResDto.AskResult askQuestion(Long memberId, String question, String stockName) throws ApiException {

        // 로그인 사용자의 성향 테스트 결과로 백테스트 investType(1~5) 확보
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        Personality personality = member.getPersonality();
        if (personality == null) {
            throw new MemberException(MemberErrorCode.PERSONALITY_NOT_FOUND);
        }
        int investType = personality.getInvestPersonality().ordinal() + 1;

        // 1차 AI 호출: 질문 내용을 분석해 백테스트/뉴스 데이터 필요 여부 + 언급된 종목명을 판단한다.
        RoutingDecision decision = classify(question);
        log.info("질문 라우팅 결과. needsBacktest: {}, needsNews: {}, 추출된 종목명: {}",
                decision.needsBacktest(), decision.needsNews(), decision.stockName());

        // 명시적으로 넘어온 종목명(예: 종목 상세페이지에서 호출)을 우선하고, 없으면 질문에서 추출된 종목명을 쓴다.
        String candidateName = (stockName != null && !stockName.isBlank()) ? stockName : decision.stockName();

        String stockCode = null;
        if (candidateName != null && !candidateName.isBlank()) {
            Optional<Stock> stock = stockRepository.findByStockName(candidateName);
            if (stock.isEmpty()) {
                // DB에 없는 종목명이면 조용히 넘어가지 않고, 2차 AI 호출 없이 바로 명확하게 안내한다.
                log.info("지원하지 않는 종목명. candidateName: {}", candidateName);
                String notice = "'%s'은(는) 현재 지원하지 않는 종목입니다. 지원되는 종목명으로 다시 질문해주세요."
                        .formatted(candidateName);
                return OpenAiResDto.AskResult.builder().answer(notice).build();
            }
            stockCode = stock.get().getStockCode();
        }

        boolean hasStock = stockCode != null;
        boolean hasBacktest = hasStock && decision.needsBacktest();
        boolean hasNews = hasStock && decision.needsNews();

        // 2차 AI 호출: 실제로 필요한 섹션만 채워서 프롬프트를 조립하고(불필요한 placeholder 없음),
        // 시스템 프롬프트도 어떤 데이터를 쓰는지에 맞춰 조건부로 구성한다.
        List<String> sections = new ArrayList<>();
        if (hasBacktest) {
            sections.add("[백테스트 스코어]\n" + buildBacktestSummary(stockCode, investType));
        }
        if (hasNews) {
            sections.add("[관련 뉴스]\n" + formatNews(pineconeService.searchData(question, candidateName)));
        }
        sections.add("[질문]\n" + question);
        String userText = String.join("\n\n", sections);

        SystemMessage systemMessage = new SystemMessage(buildSystemPrompt(hasBacktest, hasNews));
        UserMessage userMessage = new UserMessage(userText);

        String result = chatModel.call(userMessage, systemMessage);
        return OpenAiResDto.AskResult.builder().answer(result).build();
    }

    /***
     * 함수 기능: 실제로 답변에 쓰이는 데이터 조합(백테스트/뉴스 유무)에 맞춰 시스템 프롬프트를 조립한다.
     *          쓰지 않는 데이터에 대한 안내를 넣는 대신, 실제 쓰는 데이터에 맞는 지시만 포함시킨다.
     */
    private String buildSystemPrompt(boolean hasBacktest, boolean hasNews) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 주식 투자 초보자를 위한 비서야. 아래 원칙을 지켜서 답변해.\n");
        sb.append("- 초보자도 이해할 수 있는 쉬운 말로 설명하되, 답변은 충분히 구체적이고 자세하게 작성해. 짧게 뭉뚱그리지 마.\n");

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
            sb.append("- 지금은 특정 종목의 수치·뉴스 데이터가 제공되지 않았어. 일반적인 투자 지식 범위에서만 답변하고, ")
                    .append("특정 수치나 최근 소식을 지어내지 마.\n");
        }
        sb.append("- 제공되지 않은 수치나 뉴스 내용을 지어내지 말고, 실제로 갖고 있는 정보 안에서만 답변해.\n");
        sb.append("- 가능하면 (1) 핵심 요약 (2) 근거 상세 설명 (3) 참고할 점/주의사항 순서로 답변을 구성해.\n");
        return sb.toString();
    }

    /***
     * 함수 기능: 사용자 질문을 분석해 답변 생성 시 백테스트/뉴스 데이터가 필요한지, 질문에 특정 종목이
     *          언급됐는지를 판단한다. 분류 호출이 실패하거나 파싱에 실패하면 안전하게 둘 다 사용하는 것으로 대체한다.
     */
    private RoutingDecision classify(String question) {

        BeanOutputConverter<RoutingDecision> converter = new BeanOutputConverter<>(RoutingDecision.class);

        SystemMessage systemMessage = new SystemMessage(
                "너는 주식 투자 챗봇의 라우팅 어시스턴트야. 사용자 질문에 답하기 위해 어떤 데이터가 필요한지만 판단해.");

        String userText = """
                질문: %s

                판단 기준:
                - needsBacktest: 수익률, 변동성, 적합도 등 정량적인 백테스트 데이터가 필요하면 true
                - needsNews: 최근 이슈, 실적, 사건 등 뉴스 맥락이 필요하면 true
                - 두 데이터 모두 필요 없는 일반적인 투자 개념 질문이면 둘 다 false
                - stockName: 질문에서 특정 종목(예: "삼성전자", "이 종목")이 명확히 언급/암시되면 그 종목명을 그대로 적어줘.
                  종목명을 알 수 없거나(예: "이 종목"이라고만 하고 실제 이름이 없음) 특정 종목에 대한 질문이 아니면 null로 남겨줘.
                  확실하지 않으면 추측해서 채우지 말고 null로 남겨줘.

                %s
                """.formatted(question, converter.getFormat());
        UserMessage userMessage = new UserMessage(userText);

        try {
            String raw = chatModel.call(userMessage, systemMessage);
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
