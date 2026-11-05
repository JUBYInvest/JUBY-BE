package juby.invest.domain.community.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public class CommentResDto {

    @Builder
    public record CommentInfo(
            Long commentId,
            Long postId,
            String content,
            String writerNickname,
            int likeCount,
            boolean likedByMe,
            boolean isMine,
            boolean isEdited,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ){}

    @Builder
    public record CommentList(
            List<CommentInfo> comments,
            Long nextCursor, // 다음 페이지 요청 시 cursor로 보낸다. 마지막 페이지면 null.
            boolean hasNext
    ){}

    @Builder
    public record LikeRes(
            Long commentId,
            boolean liked,
            int likeCount
    ){}
}
