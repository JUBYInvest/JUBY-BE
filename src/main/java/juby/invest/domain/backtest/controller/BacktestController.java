package juby.invest.domain.backtest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.domain.backtest.dto.BacktestReqDto;
import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.exception.code.BacktestSuccessCode;
import juby.invest.domain.backtest.service.BacktestService;
import juby.invest.global.security.enitty.CustomOAuth2User;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backtest")
@Tag(name = "백테스트 API", description = "백테스트 전략을 시행하고, 결과 지표를 반환한다.")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestService backtestService;

    /***
     *
     * @return
     */
    @Operation(summary = "백테스트 실행", description = "종목 코드와 전략 번호를 전달해주면 해당 전략을 실행한다.")
    @PostMapping("/run")
    public ApiResponse<BacktestResDto.GetInfo> convert(
            @AuthenticationPrincipal CustomOAuth2User customOAuth2User,
            @RequestBody BacktestReqDto.ReqInfo dto){

        BaseSuccessCode successCode = BacktestSuccessCode.OK;
        log.info(customOAuth2User.getName());
        return ApiResponse.onSuccess(successCode, backtestService.runStrategy(dto));
    }
}
