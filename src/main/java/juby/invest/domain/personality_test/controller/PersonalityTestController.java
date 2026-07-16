package juby.invest.domain.personality_test.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import juby.invest.domain.personality_test.dto.TestQuestionList;
import juby.invest.domain.personality_test.dto.TestResponseDto;
import juby.invest.domain.personality_test.exception.code.PersonalityTestSuccessCode;
import juby.invest.domain.personality_test.service.PersonalityTestService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "성향 테스트 API", description = "사용자의 투자 성향 테스트 조회, 응답을 반환한다.")
public class PersonalityTestController {

    private final PersonalityTestService personalityTestService;

    @GetMapping("/personality-tests")
    @Operation(description = "사용자 투자 성향 조회")
    public ApiResponse<TestQuestionList> getQuestions(){
        BaseSuccessCode successCode = PersonalityTestSuccessCode.PERSONALITY_TEST_SUCCESS_CODE;

        return ApiResponse.onSuccess(successCode, personalityTestService.getQuestions());
    }

    @PostMapping("/personality-tests")
    @Operation(description = "사용자 투자 성향 측정")
    public ApiResponse<TestResponseDto.TestResultRes> definPersonality(
            @AuthenticationPrincipal CustomOAuth2User user,
            @Valid @RequestBody TestResponseDto.TestResultReq dto
    ){
        BaseSuccessCode successCode = PersonalityTestSuccessCode.OK;
        return ApiResponse.onSuccess(successCode, personalityTestService.calculatePersonality(user.getId(), dto));
    }
}
