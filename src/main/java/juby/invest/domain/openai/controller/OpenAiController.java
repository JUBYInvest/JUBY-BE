package juby.invest.domain.openai.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/open-ai")
@Slf4j
public class OpenAiController {

    private final OpenAiService openAiService;

    @GetMapping("/test")
    public ApiResponse<OpenAiResDto.AskResult> requestQuestion(){
        BaseSuccessCode successCode = GeneralSuccessCode.OK;

        OpenAiResDto.AskResult result = openAiService.test();

        return ApiResponse.onSuccess(successCode, result);
    }

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
