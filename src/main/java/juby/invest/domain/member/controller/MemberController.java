package juby.invest.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.domain.member.dto.MemberResDto;
import juby.invest.domain.member.exception.code.MemberSuccessCode;
import juby.invest.domain.member.service.MemberService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member")
@Tag(name = "마이페이지 API", description = "회원 정보 조회, 투자유형 조회, 회원 탈퇴를 제공한다.")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "내 정보 확인", description = "소셜 로그인으로 받은 이름, 이메일, 생일 정보를 반환한다.")
    @GetMapping("/info")
    public ApiResponse<MemberResDto.MemberInfo> getMemberInfo(
            @AuthenticationPrincipal CustomOAuth2User customOAuth2User) {

        return ApiResponse.onSuccess(
                MemberSuccessCode.OK,
                memberService.getMemberInfo(customOAuth2User.getId()));
    }

    @Operation(summary = "투자유형 보기", description = "회원가입 시 진행한 투자성향테스트 결과를 반환한다.")
    @GetMapping("/personality")
    public ApiResponse<MemberResDto.PersonalityInfo> getPersonalityInfo(
            @AuthenticationPrincipal CustomOAuth2User customOAuth2User) {

        return ApiResponse.onSuccess(
                MemberSuccessCode.PERSONALITY_OK,
                memberService.getPersonalityInfo(customOAuth2User.getId()));
    }

    @Operation(summary = "회원 탈퇴", description = "현재 로그인된 회원을 삭제한다.")
    @DeleteMapping
    public ApiResponse<Void> deleteMember(
            @AuthenticationPrincipal CustomOAuth2User customOAuth2User) {

        memberService.deleteMember(customOAuth2User.getId());
        return ApiResponse.onSuccess(MemberSuccessCode.DELETE_OK, null);
    }
}
