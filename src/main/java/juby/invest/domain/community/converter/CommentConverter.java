package juby.invest.domain.community.converter;

import juby.invest.domain.community.dto.CommentResDto;
import juby.invest.domain.community.entity.Comment;

import java.util.List;

public class CommentConverter {

    public static CommentResDto.CommentInfo toInfo(Comment comment, Long postId, Long viewerId, boolean likedByMe) {
        return CommentResDto.CommentInfo.builder()
                .commentId(comment.getId())
                .postId(postId)
                .content(comment.getContent())
                .writerNickname(WriterConverter.nickname(comment.getMember()))
                .likeCount(comment.getLikeCount())
                .likedByMe(likedByMe)
                .isMine(comment.isWrittenBy(viewerId))
                .isEdited(comment.isEdited())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    public static CommentResDto.CommentList toCommentList(List<CommentResDto.CommentInfo> comments, boolean hasNext) {
        Long nextCursor = hasNext ? comments.getLast().commentId() : null;
        return CommentResDto.CommentList.builder()
                .comments(comments)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }
}
