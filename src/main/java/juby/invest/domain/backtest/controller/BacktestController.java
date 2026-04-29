package juby.invest.domain.backtest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.domain.backtest.dto.BacktestResDTO;
import juby.invest.domain.backtest.exception.code.BacktestSuccessCode;
import juby.invest.domain.backtest.service.BacktestService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backtest")
@Tag(name = "백테스트 API", description = "백테스트 전략을 시행하고, 결과 지표를 반환한다.")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestService backtestService;

    /***
     *
     * @param stockCode
     * @param strategyName
     * @return
     */
    @Operation(summary = "백테스트 실행", description = "종목 코드와 전략 번호를 전달해주면 해당 전략을 실행한다.")
    @GetMapping("/run")
    public ApiResponse<BacktestResDTO.GetInfo> convert(
            @Parameter(description = "종목 코드") @RequestParam("stock_code") String stockCode,
            @Parameter(description = "전략 이름 (예) smaStrategy)") @RequestParam("strategy_name") String strategyName){

        BaseSuccessCode successCode = BacktestSuccessCode.OK;

        return ApiResponse.onSuccess(successCode, backtestService.runStrategy(stockCode, strategyName));
    }
}
