package juby.invest.domain.chat.converter;

import juby.invest.domain.chat.dto.ChatResDto;
import juby.invest.domain.chat.entity.ChatContent;
import juby.invest.domain.chat.entity.ChatSession;

import java.util.List;

public class ChatConverter {

    public static ChatResDto.SessionSummary toSessionSummary(ChatSession session) {
        return ChatResDto.SessionSummary.builder()
                .chatSessionId(session.getId())
                .title(session.getTitle())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    public static ChatResDto.SessionDetail toSessionDetail(ChatSession session, List<ChatContent> messages) {
        return ChatResDto.SessionDetail.builder()
                .chatSessionId(session.getId())
                .title(session.getTitle())
                .createdAt(session.getCreatedAt())
                .messages(messages.stream().map(ChatConverter::toMessage).toList())
                .build();
    }

    public static ChatResDto.MessageDto toMessage(ChatContent content) {
        return ChatResDto.MessageDto.builder()
                .messageId(content.getId())
                .role(content.getRole())
                .content(content.getContent())
                .createdAt(content.getCreatedAt())
                .build();
    }
}