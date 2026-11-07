package juby.invest.domain.livechat.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public class LiveChatResDto {

    // 채팅 메시지 한 건. 이전 대화 조회와 실시간 브로드캐스트가 같은 모양을 쓴다.
    @Builder
    public record Message(
            Long messageId,
            String writerNickname,
            String content,
            LocalDateTime createdAt
    ){}

    @Builder
    public record MessageList(
            List<Message> messages, // 오래된 메시지가 먼저 온다. (화면 위 -> 아래 순서)
            Long nextCursor,        // 더 이전 대화를 볼 때 cursor로 보낸다. 마지막 페이지면 null.
            boolean hasNext
    ){}
}
