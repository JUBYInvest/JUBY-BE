package juby.invest.domain.openai.controller;

import juby.invest.domain.openai.service.OpenAiService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import juby.invest.global.apiPayload.code.GeneralSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.db_data.client.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/open-ai")
@Slf4j
public class OpenAiController {

    private final OpenAiService openAiService;

    @GetMapping("/test")
    public ApiResponse<Void> requestQuestion(){
        BaseSuccessCode successCode = GeneralSuccessCode.OK;

        openAiService.test();

        return ApiResponse.onSuccess(successCode, null);
    }

    @GetMapping("/ask")
    public ApiResponse<Void> askQuestion(
            @RequestParam("question") String question,
            @RequestParam("stock_name") String stockName
    ) throws ApiException {
        BaseSuccessCode successCode = GeneralSuccessCode.OK;
        openAiService.askQuestion(question, stockName);
        return ApiResponse.onSuccess(successCode, null);
    }
}
