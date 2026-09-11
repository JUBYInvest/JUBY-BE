package juby.invest.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

public class LikeStockDto {

    public record LikeStockReq(

            @Schema(description = "종목코드", example = "005930")
            @NotBlank(message = "종목코드는 필수입니다.")
            @Pattern(regexp = "\\d{6}", message = "종목코드는 6자리 숫자여야 합니다.")
            String stockCode
    ){}

    @Builder
    public record LikeStockRes(
            String stockCode,
            String stockName,
            boolean liked
    ){
        public static LikeStockRes of(String stockCode, String stockName, boolean liked){
            return LikeStockRes.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .liked(liked)
                    .build();
        }
    }
}
