package juby.invest.domain.member.exception.code.likeStock;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LikeStockErrorCode implements BaseErrorCode {

    ALREADY_LIKED(HttpStatus.CONFLICT,"LIKE409_1", "이미 관심 종목으로 등록된 종목입니다."),
    LIKE_STOCK_NOT_FOUND(HttpStatus.NOT_FOUND,"LIKE404_1","해당 관심 종목을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
