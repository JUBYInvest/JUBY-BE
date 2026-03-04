package juby.invest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.dto.CurrentPriceDto;
import juby.invest.service.MarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "시세 조회 API", description = "주식의 현재 정보를 조회한다.")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/market")
public class MarketController {

    private final MarketService marketService;

    @Operation(summary = "주식 현재가 및 전일대비 조회", description = "종목 코드를 입력 받아 현재가와 전일대비를 반환한다.")
    @GetMapping("/price")
    public ResponseEntity<CurrentPriceDto.Output> getPrice(@Parameter(description = "종목 코드")
            @RequestParam String code){
        CurrentPriceDto.Output response = marketService.getCurrentPrice(code);
        return ResponseEntity.ok(response);
    }
}
