package juby.invest.domain.member.exception.code.member;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberSuccessCode implements BaseSuccessCode {

    OK(HttpStatus.OK,
            "MEMBER200_1",
            "회원을 성공적으로 조회하였습니다."),
    PERSONALITY_OK(HttpStatus.OK,
            "MEMBER200_2",
            "투자유형을 성공적으로 조회하였습니다."),
    DELETE_OK(HttpStatus.OK,
            "MEMBER200_3",
            "회원이 성공적으로 탈퇴되었습니다."),
    PERSONALITY_CHANGE_OK(HttpStatus.OK,
              "MEMBER200_4",
            "투자유형이 성공적으로 변경되었습니다."),
    INFO_CHANGE_OK(HttpStatus.OK,
            "MEMBER200_4",
            "사용자 정보가 성공적으로 변경되었습니다.")
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
