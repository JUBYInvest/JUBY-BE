package juby.invest.domain.chat.dto;

import juby.invest.domain.chat.enums.ChatRole;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public class ChatResDto {

    // 사이드바 대화방 목록에 쓰이는 요약 정보
    @Builder
    public record SessionSummary(
            Long chatSessionId,
            String title,
            LocalDateTime updatedAt
    ) {}

    // 대화방 상세 조회(전체 메시지 포함)
    @Builder
    public record SessionDetail(
            Long chatSessionId,
            String title,
            LocalDateTime createdAt,
            List<MessageDto> messages
    ) {}

    @Builder
    public record MessageDto(
            Long messageId,
            ChatRole role,
            String content,
            LocalDateTime createdAt
    ) {}
}