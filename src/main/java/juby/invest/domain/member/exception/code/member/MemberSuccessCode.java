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
            "MEMBER200_5",
            "사용자 정보가 성공적으로 변경되었습니다."),
    NICKNAME_CHECK_OK(HttpStatus.OK,
            "MEMBER200_6",
            "닉네임 중복 확인을 완료하였습니다."),
    NICKNAME_CHANGE_OK(HttpStatus.OK,
            "MEMBER200_7",
            "닉네임이 성공적으로 설정되었습니다.")
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
