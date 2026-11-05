package juby.invest.domain.community.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public class PostResDto {

    // 목록의 게시물 한 건. 본문은 미리보기만 담는다.
    @Builder(toBuilder = true)
    public record PostSummary(
            Long postId,
            String stockCode, // 종목 없는 글이면 null
            String stockName,
            Double changeRate, // 종목의 전일 대비 등락률(%). 종목 없는 글이거나 시세 조회에 실패하면 null
            String title,
            String preview,
            String writerNickname,
            int likeCount,
            int commentCount,
            boolean likedByMe,
            boolean isMine,
            boolean isEdited,
            LocalDateTime createdAt
    ){}

    @Builder(toBuilder = true)
    public record PostList(
            List<PostSummary> posts,
            Long nextCursor, // 다음 페이지 요청 시 cursor로 보낸다. 마지막 페이지면 null.
            boolean hasNext
    ){}

    // 인기글 목록. 좋아요 수가 같은 글이 많아 (좋아요 수, id) 두 값으로 다음 페이지를 이어 본다.
    @Builder(toBuilder = true)
    public record PopularPostList(
            List<PostSummary> posts,
            Integer nextCursorLikeCount, // 다음 페이지 요청 시 cursorLikeCount로 보낸다. 마지막 페이지면 null.
            Long nextCursorId,           // 다음 페이지 요청 시 cursorId로 보낸다. 마지막 페이지면 null.
            boolean hasNext
    ){}

    @Builder
    public record PostDetail(
            Long postId,
            String stockCode, // 종목 없는 글이면 null
            String stockName,
            String title,
            String content,
            String writerNickname,
            int likeCount,
            int commentCount,
            boolean likedByMe,
            boolean isMine,
            boolean isEdited,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ){}

    @Builder
    public record LikeRes(
            Long postId,
            boolean liked,
            int likeCount
    ){}
}
