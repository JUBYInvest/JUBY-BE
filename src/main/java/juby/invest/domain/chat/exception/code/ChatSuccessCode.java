package juby.invest.domain.chat.exception.code;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatSuccessCode implements BaseSuccessCode {

    SESSION_CREATE_OK(HttpStatus.CREATED, "CHAT201_1", "대화방이 생성되었습니다."),
    SESSION_LIST_OK(HttpStatus.OK, "CHAT200_1", "대화방 목록을 조회했습니다."),
    SESSION_DETAIL_OK(HttpStatus.OK, "CHAT200_2", "대화방 상세 정보를 조회했습니다."),
    TITLE_UPDATE_OK(HttpStatus.OK, "CHAT200_3", "대화방 제목을 변경했습니다."),
    SESSION_DELETE_OK(HttpStatus.OK, "CHAT200_4", "대화방을 삭제했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}