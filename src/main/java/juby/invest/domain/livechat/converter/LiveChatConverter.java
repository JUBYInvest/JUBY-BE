package juby.invest.domain.livechat.converter;

import juby.invest.domain.community.converter.WriterConverter;
import juby.invest.domain.livechat.dto.LiveChatResDto;
import juby.invest.domain.livechat.entity.LiveChatMessage;

import java.util.List;

public class LiveChatConverter {

    public static LiveChatResDto.Message toMessage(LiveChatMessage message) {
        return LiveChatResDto.Message.builder()
                .messageId(message.getId())
                // 탈퇴 회원 표시 규칙을 커뮤니티와 맞춘다.
                .writerNickname(WriterConverter.nickname(message.getMember()))
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }

    /***
     * @param oldestFirst 오래된 메시지가 먼저 오도록 정렬된 페이지
     * @param hasNext 더 이전 메시지가 있는지
     */
    public static LiveChatResDto.MessageList toMessageList(List<LiveChatMessage> oldestFirst, boolean hasNext) {
        return LiveChatResDto.MessageList.builder()
                .messages(oldestFirst.stream().map(LiveChatConverter::toMessage).toList())
                .nextCursor(hasNext ? oldestFirst.get(0).getId() : null)
                .hasNext(hasNext)
                .build();
    }
}
