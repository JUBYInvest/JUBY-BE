package juby.invest.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import juby.invest.domain.member.dto.ChangeInvestType;
import juby.invest.domain.member.dto.ChangeMemberInfo;
import juby.invest.domain.member.dto.MemberResDto;
import juby.invest.domain.member.exception.code.member.MemberSuccessCode;
import juby.invest.domain.member.service.MemberService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@Tag(name = "마이페이지 API", description = "회원 정보 조회, 투자유형 조회, 회원 탈퇴를 제공한다.")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "내 정보 조회", description = "소셜 로그인으로 받은 이름, 이메일, 생일 정보를 반환한다.")
    @GetMapping("/me")
    public ApiResponse<MemberResDto.MemberInfo> getMemberInfo(
            @AuthenticationPrincipal CustomOAuth2User customOAuth2User) {

        return ApiResponse.onSuccess(
                MemberSuccessCode.OK,
                memberService.getMemberInfo(customOAuth2User.getId()));
    }

    @Operation(summary = "내 정보 수정", description = "나의 이름, 생일을 수정한다.")
    @PatchMapping("/me")
    public ApiResponse<ChangeMemberInfo.ChangeInfoRes> changeInfo(
            @Valid @RequestBody ChangeMemberInfo.ChangeInfoReq dto,
            @AuthenticationPrincipal CustomOAuth2User principal
    ){
        BaseSuccessCode successCode = MemberSuccessCode.INFO_CHANGE_OK;
        return ApiResponse.onSuccess(successCode, memberService.changeMemberInfo(principal, dto));
    }

    @Operation(summary = "내 투자유형 보기", description = "회원가입 시 진행한 투자성향테스트 결과를 반환한다.")
    @GetMapping("/me/personality")
    public ApiResponse<MemberResDto.PersonalityInfo> getPersonalityInfo(
            @AuthenticationPrincipal CustomOAuth2User customOAuth2User) {

        return ApiResponse.onSuccess(
                MemberSuccessCode.PERSONALITY_OK,
                memberService.getPersonalityInfo(customOAuth2User.getId()));
    }

    @Operation(summary = "내 투자유형 변경", description = "나의 투자성향을 변경한다.")
    @PatchMapping("/me/personality")
    public ApiResponse<ChangeInvestType.PersonalityRes> changePersonality(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @Valid @RequestBody ChangeInvestType.PersonalityReq dto
    ){
        BaseSuccessCode successCode = MemberSuccessCode.PERSONALITY_CHANGE_OK;
        return ApiResponse.onSuccess(successCode, memberService.changePersonality(principal, dto));
    }

    @Operation(summary = "회원 탈퇴", description = "현재 로그인된 회원을 삭제한다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteMember(
            @AuthenticationPrincipal CustomOAuth2User customOAuth2User) {

        memberService.deleteMember(customOAuth2User.getId());
        return ApiResponse.onSuccess(MemberSuccessCode.DELETE_OK, null);
    }
}
