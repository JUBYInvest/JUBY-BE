package juby.invest.domain.community.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import juby.invest.domain.community.dto.CommentReqDto;
import juby.invest.domain.community.dto.CommentResDto;
import juby.invest.domain.community.exception.code.CommunitySuccessCode;
import juby.invest.domain.community.service.CommentService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/community")
@Tag(name = "커뮤니티 댓글 API", description = "게시물 댓글 목록 조회, 작성, 수정, 삭제, 좋아요를 제공한다. 게시물과 달리 댓글은 조회도 로그인이 필요하다.")
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글 목록 조회",
            description = "게시물의 댓글을 작성순(오래된 순)으로 반환한다. 다음 페이지는 응답의 nextCursor를 cursor로 보내 조회한다.")
    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<CommentResDto.CommentList> getComments(
            @AuthenticationPrincipal CustomOAuth2User user,
            @PathVariable Long postId,
            @Parameter(description = "이전 응답의 nextCursor (첫 페이지면 생략)")
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.") @Max(value = 50, message = "size는 50 이하여야 합니다.") int size
    ) {
        return ApiResponse.onSuccess(CommunitySuccessCode.COMMENT_LIST_OK, commentService.getComments(user.getId(), postId, cursor, size));
    }

    @Operation(summary = "댓글 작성", description = "닉네임을 설정하지 않은 회원은 COMMUNITY403_1 에러가 반환된다.")
    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<CommentResDto.CommentInfo> createComment(
            @AuthenticationPrincipal CustomOAuth2User user,
            @PathVariable Long postId,
            @Valid @RequestBody CommentReqDto.ContentReq dto
    ) {
        return ApiResponse.onSuccess(CommunitySuccessCode.COMMENT_CREATE_OK, commentService.createComment(user.getId(), postId, dto));
    }

    @Operation(summary = "댓글 수정", description = "본인이 작성한 댓글만 수정할 수 있다.")
    @PatchMapping("/comments/{commentId}")
    public ApiResponse<CommentResDto.CommentInfo> updateComment(
            @AuthenticationPrincipal CustomOAuth2User user,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentReqDto.ContentReq dto
    ) {
        return ApiResponse.onSuccess(CommunitySuccessCode.COMMENT_UPDATE_OK, commentService.updateComment(user.getId(), commentId, dto));
    }

    @Operation(summary = "댓글 삭제", description = "본인이 작성한 댓글만 삭제할 수 있다.")
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(
            @AuthenticationPrincipal CustomOAuth2User user,
            @PathVariable Long commentId
    ) {
        commentService.deleteComment(user.getId(), commentId);
        return ApiResponse.onSuccess(CommunitySuccessCode.COMMENT_DELETE_OK, null);
    }

    @Operation(summary = "댓글 좋아요 토글", description = "좋아요를 누르지 않았으면 누르고, 이미 눌렀으면 취소한다. 닉네임 없이도 가능하다.")
    @PostMapping("/comments/{commentId}/likes")
    public ApiResponse<CommentResDto.LikeRes> toggleLike(
            @AuthenticationPrincipal CustomOAuth2User user,
            @PathVariable Long commentId
    ) {
        return ApiResponse.onSuccess(CommunitySuccessCode.COMMENT_LIKE_OK, commentService.toggleLike(user.getId(), commentId));
    }
}
