package juby.invest.domain.community.converter;

import juby.invest.domain.community.dto.PostResDto;
import juby.invest.domain.community.entity.Post;

import java.util.List;

public class PostConverter {

    private static final int PREVIEW_LENGTH = 100;

    // 등락률(changeRate)은 DB가 아닌 시세 캐시에서 채우므로 여기서는 비워 둔다. (CommunityBoardService에서 채움)
    public static PostResDto.PostSummary toSummary(Post post, Long viewerId, boolean likedByMe) {
        return PostResDto.PostSummary.builder()
                .postId(post.getId())
                .stockCode(post.hasStock() ? post.getStock().getStockCode() : null)
                .stockName(post.hasStock() ? post.getStock().getStockName() : null)
                .title(post.getTitle())
                .preview(preview(post.getContent()))
                .writerNickname(WriterConverter.nickname(post.getMember()))
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .likedByMe(likedByMe)
                .isMine(post.isWrittenBy(viewerId))
                .isEdited(post.isEdited())
                .createdAt(post.getCreatedAt())
                .build();
    }

    public static PostResDto.PostList toPostList(List<PostResDto.PostSummary> posts, boolean hasNext) {
        Long nextCursor = hasNext ? posts.getLast().postId() : null;
        return PostResDto.PostList.builder()
                .posts(posts)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    public static PostResDto.PopularPostList toPopularPostList(List<PostResDto.PostSummary> posts, boolean hasNext) {
        PostResDto.PostSummary last = hasNext ? posts.getLast() : null;
        return PostResDto.PopularPostList.builder()
                .posts(posts)
                .nextCursorLikeCount(last == null ? null : last.likeCount())
                .nextCursorId(last == null ? null : last.postId())
                .hasNext(hasNext)
                .build();
    }

    public static PostResDto.PostDetail toDetail(Post post, Long viewerId, boolean likedByMe) {
        return PostResDto.PostDetail.builder()
                .postId(post.getId())
                .stockCode(post.hasStock() ? post.getStock().getStockCode() : null)
                .stockName(post.hasStock() ? post.getStock().getStockName() : null)
                .title(post.getTitle())
                .content(post.getContent())
                .writerNickname(WriterConverter.nickname(post.getMember()))
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .likedByMe(likedByMe)
                .isMine(post.isWrittenBy(viewerId))
                .isEdited(post.isEdited())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private static String preview(String content) {
        if (content.codePointCount(0, content.length()) <= PREVIEW_LENGTH) {
            return content;
        }
        // char 단위로 자르면 이모지(서로게이트 쌍)가 반으로 잘려 깨지므로 코드포인트 단위로 자른다.
        return content.substring(0, content.offsetByCodePoints(0, PREVIEW_LENGTH)) + "...";
    }
}
