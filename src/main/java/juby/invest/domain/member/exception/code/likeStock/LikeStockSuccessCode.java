package juby.invest.domain.member.exception.code.likeStock;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum LikeStockSuccessCode implements BaseSuccessCode {

    ADD_LIKE_SUCCESS(HttpStatus.OK, "LIKE200_1", "관심 종목 추가에 성공하였습니다."),
    DELETE_LIKE_SUCCESS(HttpStatus.OK,"LIKE200_2","관심 종목 삭제에 성공하였습니다."),
    GET_LIKELIST_SUCCESS(HttpStatus.OK,"LIKE200_3","관심 종목 목록 조회에 성공하였습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
