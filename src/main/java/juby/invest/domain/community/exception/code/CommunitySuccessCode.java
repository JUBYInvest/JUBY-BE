package juby.invest.domain.community.exception.code;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommunitySuccessCode implements BaseSuccessCode {

    POST_LIST_OK(HttpStatus.OK,
            "COMMUNITY200_1",
            "게시물 목록을 성공적으로 조회하였습니다."),
    POST_DETAIL_OK(HttpStatus.OK,
            "COMMUNITY200_2",
            "게시물을 성공적으로 조회하였습니다."),
    POST_CREATE_OK(HttpStatus.CREATED,
            "COMMUNITY201_1",
            "게시물이 성공적으로 작성되었습니다."),
    POST_UPDATE_OK(HttpStatus.OK,
            "COMMUNITY200_3",
            "게시물이 성공적으로 수정되었습니다."),
    POST_DELETE_OK(HttpStatus.OK,
            "COMMUNITY200_4",
            "게시물이 성공적으로 삭제되었습니다."),
    POST_LIKE_OK(HttpStatus.OK,
            "COMMUNITY200_5",
            "게시물 좋아요가 성공적으로 반영되었습니다."),
    COMMENT_LIST_OK(HttpStatus.OK,
            "COMMUNITY200_6",
            "댓글 목록을 성공적으로 조회하였습니다."),
    COMMENT_CREATE_OK(HttpStatus.CREATED,
            "COMMUNITY201_2",
            "댓글이 성공적으로 작성되었습니다."),
    COMMENT_UPDATE_OK(HttpStatus.OK,
            "COMMUNITY200_7",
            "댓글이 성공적으로 수정되었습니다."),
    COMMENT_DELETE_OK(HttpStatus.OK,
            "COMMUNITY200_8",
            "댓글이 성공적으로 삭제되었습니다."),
    COMMENT_LIKE_OK(HttpStatus.OK,
            "COMMUNITY200_9",
            "댓글 좋아요가 성공적으로 반영되었습니다."),
    POPULAR_POST_LIST_OK(HttpStatus.OK,
            "COMMUNITY200_10",
            "인기 게시물 목록을 성공적으로 조회하였습니다."),
    STOCK_BOARD_OK(HttpStatus.OK,
            "COMMUNITY200_11",
            "커뮤니티 종목 목록을 성공적으로 조회하였습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
