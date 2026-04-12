package juby.invest.ta4j.controller;

import juby.invest.ta4j.service.BacktestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestService backtestService;

    @GetMapping("/convert")
    public String convert(){
        backtestService.runStrategy("005930");
        return "Success";
    }
}
