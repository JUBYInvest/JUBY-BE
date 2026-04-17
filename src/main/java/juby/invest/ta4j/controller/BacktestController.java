package juby.invest.ta4j.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.ta4j.dto.BacktestResponseDto;
import juby.invest.ta4j.service.BacktestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backtest")
@Tag(name = "백테스트 API", description = "백테스트 전략을 시행하고, 결과 지표를 반환한다.")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestService backtestService;

    @Operation(summary = "백테스트 실행", description = "종목 코드와 전략 번호를 전달해주면 SMA 전략을 실행한다")
    @GetMapping("/convert")
    public ResponseEntity<BacktestResponseDto> convert(
            @Parameter(description = "종목 코드")
            @RequestParam("stockcode") String stockCode,
            @Parameter(description = "전략 이름 (smaStrategy)")
            @RequestParam("strategy_name") String strategyName){
        BacktestResponseDto response = backtestService.runStrategy(stockCode, strategyName);

        return ResponseEntity.ok(response);
    }
}
