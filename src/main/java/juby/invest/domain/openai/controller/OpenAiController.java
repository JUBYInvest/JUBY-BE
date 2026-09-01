package juby.invest.domain.openai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import juby.invest.domain.openai.dto.OpenAiReqDto;
import juby.invest.domain.openai.dto.OpenAiResDto;
import juby.invest.domain.openai.service.OpenAiService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import juby.invest.global.apiPayload.code.GeneralErrorCode;
import juby.invest.global.apiPayload.code.GeneralSuccessCode;
import juby.invest.global.apiPayload.exception.ProjectException;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.db_data.client.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/open-ai")
@Slf4j
@Tag(name = "OpenAI 챗봇 API", description = "투자성향/백테스트 프리셋/뉴스 데이터를 활용해 투자 질문에 답변하는 챗봇 API")
public class OpenAiController {

    private final OpenAiService openAiService;

    @Operation(summary = "투자 질문 답변 API",
            description = "로그인한 사용자의 투자성향(회원가입 시 저장된 값)을 기본으로 답변한다. " +
                    "먼저 질문 내용을 분석해 답변에 백테스트 프리셋 결과/관련 뉴스 데이터가 필요한지, " +
                    "질문에 종목이 언급됐는지를 판단한 뒤 그 결과에 맞춰 답변을 생성한다. " +
                    "stockName은 선택값이며, 비워서 보내면 질문 문장에서 종목명을 추론한다(종목 상세페이지에서 호출하는 " +
                    "경우처럼 종목이 명확할 때만 채워서 보내면 됨). 질문/추론된 종목명이 DB에 없는 종목이면 에러가 아니라 " +
                    "\"현재 지원하지 않는 종목입니다\" 안내 문구가 담긴 200 응답을 반환한다. " +
                    "투자성향이 아직 등록되지 않은 회원이면 404(MEMBER404_2)가 반환되니, 이 경우 투자성향 테스트로 안내해야 한다.")
    @PostMapping("/ask")
    public ApiResponse<OpenAiResDto.AskResult> askQuestion(
            @AuthenticationPrincipal CustomOAuth2User user,
            @Valid @RequestBody OpenAiReqDto.AskRequest dto
    ) throws ApiException {
        if (user == null) {
            throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        }

        BaseSuccessCode successCode = GeneralSuccessCode.OK;
        OpenAiResDto.AskResult result = openAiService.askQuestion(user.getId(), dto.question(), dto.stockName());
        return ApiResponse.onSuccess(successCode, result);
    }
}
