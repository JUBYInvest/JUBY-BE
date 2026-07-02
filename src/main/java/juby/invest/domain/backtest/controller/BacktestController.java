package juby.invest.domain.backtest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import juby.invest.domain.backtest.dto.BacktestReqDto;
import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.exception.code.BacktestSuccessCode;
import juby.invest.domain.backtest.service.BacktestService;
import juby.invest.global.security.entity.CustomOAuth2User;
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
     */
    @Operation(summary = "백테스트 실행",
            description = "종목코드, 성향번호(1:안정형, 2:안정추구형, 3:위험중립형, 4:적극투자형, 5:공격투자형), " +
                    "시작날짜, 끝날짜를 전달해주면 백테스트를 실행하고 알맞은 지표를 반환한다.")
    @GetMapping("/run")
    public ApiResponse<BacktestResDto.QuantScoringResponse> convert(
            @Valid @ModelAttribute BacktestReqDto.ReqInfo dto){

        BaseSuccessCode successCode = BacktestSuccessCode.OK;

        return ApiResponse.onSuccess(successCode, backtestService.runStrategy(dto));
    }
}
