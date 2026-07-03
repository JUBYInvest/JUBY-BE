package juby.invest.initiate.controller;


import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import juby.invest.global.apiPayload.code.GeneralSuccessCode;
import juby.invest.initiate.dto.DailyPriceRes;
import juby.invest.initiate.loader.ParticularDailyPriceLoadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class InitiateController {

    private final ParticularDailyPriceLoadService particularDailyPriceLoadService;

    @PostMapping("/initiate")
    public ApiResponse<Void> saveParticularDailyPrice(
            @RequestBody DailyPriceRes dailyPriceRes
            ) throws InterruptedException {
        BaseSuccessCode successCode = GeneralSuccessCode.OK;

        particularDailyPriceLoadService.getDailyPrice(dailyPriceRes.date());

        return ApiResponse.onSuccess(successCode, null);
    }
}
