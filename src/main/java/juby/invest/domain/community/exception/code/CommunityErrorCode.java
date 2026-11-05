package juby.invest.domain.community.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommunityErrorCode implements BaseErrorCode {

    INVALID_POPULAR_CURSOR(HttpStatus.BAD_REQUEST,
            "COMMUNITY400_1",
            "cursorLikeCount와 cursorId는 함께 보내야 합니다."),
    NICKNAME_REQUIRED(HttpStatus.FORBIDDEN,
            "COMMUNITY403_1",
            "닉네임을 설정한 후 이용할 수 있습니다."),
    NOT_POST_WRITER(HttpStatus.FORBIDDEN,
            "COMMUNITY403_2",
            "본인이 작성한 게시물만 수정/삭제할 수 있습니다."),
    NOT_COMMENT_WRITER(HttpStatus.FORBIDDEN,
            "COMMUNITY403_3",
            "본인이 작성한 댓글만 수정/삭제할 수 있습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND,
            "COMMUNITY404_1",
            "게시물을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND,
            "COMMUNITY404_2",
            "댓글을 찾을 수 없습니다."),
    LIKE_CONFLICT(HttpStatus.CONFLICT,
            "COMMUNITY409_1",
            "좋아요 요청이 동시에 처리되었습니다. 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
