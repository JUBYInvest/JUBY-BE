package juby.invest.domain.backtest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import juby.invest.domain.backtest.dto.BacktestReqDto;
import juby.invest.domain.backtest.dto.BacktestResDto;
import juby.invest.domain.backtest.exception.code.BacktestSuccessCode;
import juby.invest.domain.backtest.service.BacktestPresetService;
import juby.invest.global.security.entity.CustomOAuth2User;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/backtest")
@Tag(name = "백테스트 API", description = "기간 프리셋 기준으로 미리 계산된 백테스트 결과를 조회한다.")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestPresetService backtestPresetService;

    @Operation(summary = "백테스트 프리셋 결과 조회",
            description = "종목코드, 성향번호(1:안정형, 2:안정추구형, 3:위험중립형, 4:적극투자형, 5:공격투자형), " +
                    "기간 프리셋(ONE_MONTH, THREE_MONTHS, SIX_MONTHS, ONE_YEAR)을 전달해주면 " +
                    "매일 새벽 배치로 미리 계산되어 DB에 적재된 백테스트 결과를 반환한다. " +
                    "전략별로 지원하는 최소 기간 프리셋이 달라 그보다 짧은 기간을 요청하면 400이 반환된다.")
    @GetMapping("/preset")
    public ApiResponse<BacktestResDto.PresetResponse> getPreset(
            @Valid @ModelAttribute BacktestReqDto.ReqInfo dto){

        BaseSuccessCode successCode = BacktestSuccessCode.OK;

        return ApiResponse.onSuccess(successCode,
                backtestPresetService.getPreset(dto.stockCode(), dto.investType(), dto.period()));
    }

    @Operation(summary = "투자성향별 선택 가능한 기간 프리셋 목록 조회",
            description = "5개 투자성향(1:안정형 ~ 5:공격투자형) 각각에 대해, 실제로 DB에 계산되어 적재된(=선택 가능한) " +
                    "기간 프리셋 목록을 반환한다. 종목과 무관하게 투자성향 기준으로 결정된다. " +
                    "프론트는 사용자가 투자성향을 고르면 이 목록으로 기간 선택지를 제한하면 된다.")
    @GetMapping("/preset/options")
    public ApiResponse<List<BacktestResDto.PresetOptionsResponse>> getPresetOptions(){

        BaseSuccessCode successCode = BacktestSuccessCode.OK;

        return ApiResponse.onSuccess(successCode, backtestPresetService.getPresetOptions());
    }
}
