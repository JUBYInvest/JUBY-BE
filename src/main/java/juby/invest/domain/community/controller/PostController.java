package juby.invest.domain.community.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import juby.invest.domain.community.dto.PostReqDto;
import juby.invest.domain.community.dto.PostResDto;
import juby.invest.domain.community.exception.code.CommunitySuccessCode;
import juby.invest.domain.community.service.CommunityBoardService;
import juby.invest.domain.community.service.PostService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/community/posts")
@Tag(name = "커뮤니티 게시물 API", description = "종목 커뮤니티 게시물 목록/상세 조회, 작성, 수정, 삭제, 좋아요를 제공한다. 조회는 비로그인도 가능하다.")
public class PostController {

    private final PostService postService;
    private final CommunityBoardService communityBoardService;

    @Operation(summary = "게시물 목록 조회 (최신순)",
            description = "게시물을 최신순으로 반환한다. stockCode가 없으면 전체(종목 없는 글 포함), 있으면 해당 종목 게시물만 반환한다. " +
                    "다음 페이지는 응답의 nextCursor를 cursor로 보내 조회한다. changeRate는 최대 5분 전 시세다.")
    @GetMapping
    public ApiResponse<PostResDto.PostList> getPosts(
            @AuthenticationPrincipal CustomOAuth2User user,
            @Parameter(description = "종목 코드 (없으면 전체)")
            @RequestParam(required = false) @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다.") String stockCode,
            @Parameter(description = "이전 응답의 nextCursor (첫 페이지면 생략)")
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.") @Max(value = 50, message = "size는 50 이하여야 합니다.") int size
    ) {
        return ApiResponse.onSuccess(CommunitySuccessCode.POST_LIST_OK,
                communityBoardService.getLatestPosts(viewerId(user), stockCode, cursor, size));
    }

    @Operation(summary = "이번 주 인기 게시물 조회",
            description = "이번 주(월요일 0시부터) 작성된 게시물을 좋아요 많은 순으로 반환한다. 좋아요 수가 같으면 최신 글이 먼저다. " +
                    "다음 페이지는 응답의 nextCursorLikeCount, nextCursorId를 cursorLikeCount, cursorId로 함께 보내 조회한다.")
    @GetMapping("/popular")
    public ApiResponse<PostResDto.PopularPostList> getPopularPosts(
            @AuthenticationPrincipal CustomOAuth2User user,
            @Parameter(description = "종목 코드 (없으면 전체)")
            @RequestParam(required = false) @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다.") String stockCode,
            @Parameter(description = "이전 응답의 nextCursorLikeCount (첫 페이지면 생략)")
            @RequestParam(required = false) Integer cursorLikeCount,
            @Parameter(description = "이전 응답의 nextCursorId (첫 페이지면 생략)")
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.") @Max(value = 50, message = "size는 50 이하여야 합니다.") int size
    ) {
        return ApiResponse.onSuccess(CommunitySuccessCode.POPULAR_POST_LIST_OK,
                communityBoardService.getPopularPosts(viewerId(user), stockCode, cursorLikeCount, cursorId, size));
    }

    @Operation(summary = "게시물 상세 조회")
    @GetMapping("/{postId}")
    public ApiResponse<PostResDto.PostDetail> getPost(
            @AuthenticationPrincipal CustomOAuth2User user,
            @PathVariable Long postId
    ) {
        return ApiResponse.onSuccess(CommunitySuccessCode.POST_DETAIL_OK, postService.getPost(viewerId(user), postId));
    }

    @Operation(summary = "게시물 작성", description = "stockCode는 선택 사항이다. 닉네임을 설정하지 않은 회원은 COMMUNITY403_1 에러가 반환된다.")
    @PostMapping
    public ApiResponse<PostResDto.PostDetail> createPost(
            @AuthenticationPrincipal CustomOAuth2User user,
            @Valid @RequestBody PostReqDto.CreateReq dto
    ) {
        return ApiResponse.onSuccess(CommunitySuccessCode.POST_CREATE_OK, postService.createPost(user.getId(), dto));
    }

    @Operation(summary = "게시물 수정", description = "본인이 작성한 게시물만 수정할 수 있다. 종목은 변경할 수 없다.")
    @PatchMapping("/{postId}")
    public ApiResponse<PostResDto.PostDetail> updatePost(
            @AuthenticationPrincipal CustomOAuth2User user,
            @PathVariable Long postId,
            @Valid @RequestBody PostReqDto.UpdateReq dto
    ) {
        return ApiResponse.onSuccess(CommunitySuccessCode.POST_UPDATE_OK, postService.updatePost(user.getId(), postId, dto));
    }

    @Operation(summary = "게시물 삭제", description = "본인이 작성한 게시물만 삭제할 수 있다.")
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @AuthenticationPrincipal CustomOAuth2User user,
            @PathVariable Long postId
    ) {
        postService.deletePost(user.getId(), postId);
        return ApiResponse.onSuccess(CommunitySuccessCode.POST_DELETE_OK, null);
    }

    @Operation(summary = "게시물 좋아요 토글", description = "좋아요를 누르지 않았으면 누르고, 이미 눌렀으면 취소한다. 닉네임 없이도 가능하다.")
    @PostMapping("/{postId}/likes")
    public ApiResponse<PostResDto.LikeRes> toggleLike(
            @AuthenticationPrincipal CustomOAuth2User user,
            @PathVariable Long postId
    ) {
        return ApiResponse.onSuccess(CommunitySuccessCode.POST_LIKE_OK, postService.toggleLike(user.getId(), postId));
    }

    // 조회 API는 비로그인도 허용하므로 인증 객체가 없을 수 있다.
    private Long viewerId(CustomOAuth2User user) {
        return user == null ? null : user.getId();
    }
}
