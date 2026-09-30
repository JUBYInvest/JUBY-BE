package juby.invest.domain.chat.service;

import juby.invest.domain.chat.converter.ChatConverter;
import juby.invest.domain.chat.dto.ChatResDto;
import juby.invest.domain.chat.entity.ChatContent;
import juby.invest.domain.chat.entity.ChatSession;
import juby.invest.domain.chat.enums.ChatRole;
import juby.invest.domain.chat.exception.ChatException;
import juby.invest.domain.chat.exception.code.ChatErrorCode;
import juby.invest.domain.chat.repository.ChatContentRepository;
import juby.invest.domain.chat.repository.ChatSessionRepository;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final String DEFAULT_TITLE = "새 대화";
    private static final int MAX_TITLE_LENGTH = 30;
    // 답변 생성 선점 후 이 시간이 지나도 해제되지 않았다면(서버 중단 등) 멈춘 것으로 보고 다시 선점을 허용한다.
    private static final Duration ANSWERING_STALE_TIMEOUT = Duration.ofMinutes(5);

    private final ChatSessionRepository chatSessionRepository;
    private final ChatContentRepository chatContentRepository;
    private final MemberRepository memberRepository;

    // ===== 대화방 CRUD API =====

    @Transactional
    public ChatResDto.SessionSummary createSession(Long memberId) {
        Member member = getMember(memberId);
        ChatSession session = chatSessionRepository.save(
                ChatSession.builder().member(member).title(DEFAULT_TITLE).build());
        return ChatConverter.toSessionSummary(session);
    }

    public List<ChatResDto.SessionSummary> getSessions(Long memberId) {
        return chatSessionRepository.findByMember_IdOrderByUpdatedAtDesc(memberId).stream()
                .map(ChatConverter::toSessionSummary)
                .toList();
    }

    public ChatResDto.SessionDetail getSessionDetail(Long memberId, Long chatSessionId) {
        ChatSession session = getOwnedSession(memberId, chatSessionId);
        List<ChatContent> messages = chatContentRepository.findByChatSession_IdOrderByIdAsc(chatSessionId);
        return ChatConverter.toSessionDetail(session, messages);
    }

    @Transactional
    public ChatResDto.SessionSummary updateTitle(Long memberId, Long chatSessionId, String rawTitle) {
        ChatSession session = getOwnedSession(memberId, chatSessionId);
        session.updateTitle(normalizeTitle(rawTitle, false));
        return ChatConverter.toSessionSummary(session);
    }

    @Transactional
    public void deleteSession(Long memberId, Long chatSessionId) {
        ChatSession session = getOwnedSession(memberId, chatSessionId);
        chatContentRepository.deleteByChatSession_Id(chatSessionId);
        chatSessionRepository.delete(session);
    }

    // ===== OpenAiService에서 사용하는 내부용 메서드 =====

    /***
     * 함수 기능: chatSessionId가 있으면 본인 소유 대화방인지 검증 후 반환하고,
     *          없으면 첫 질문을 바탕으로 제목을 자동 생성해 새 대화방을 만든다.
     */
    @Transactional
    public ChatSession getOrCreateSession(Long memberId, Long chatSessionId, String firstQuestion) {
        if (chatSessionId != null) {
            return getOwnedSession(memberId, chatSessionId);
        }
        Member member = getMember(memberId);
        return chatSessionRepository.save(
                ChatSession.builder()
                        .member(member)
                        .title(normalizeTitle(firstQuestion, true))
                        .build());
    }

    /***
     * 함수 기능: 빈 대화방(대화방 생성 API로 먼저 만든 경우)에 첫 질문이 들어오면, 제목이 아직 기본값일 때만
     *          첫 질문을 바탕으로 제목을 자동 생성한다. 사용자가 직접 바꾼 제목은 덮어쓰지 않는다.
     */
    @Transactional
    public void applyAutoTitleIfDefault(Long chatSessionId, String firstQuestion) {
        ChatSession session = chatSessionRepository.findById(chatSessionId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.SESSION_NOT_FOUND));
        if (DEFAULT_TITLE.equals(session.getTitle())) {
            session.updateTitle(normalizeTitle(firstQuestion, true));
        }
    }

    /***
     * 함수 기능: OpenAI 문맥에 포함할 직전 대화 이력을 오래된 순으로 최대 limit개 조회한다.
     *          (이번 턴의 질문은 포함하지 않은, 순수한 "이전" 대화만 대상으로 한다)
     */
    public List<ChatContent> getRecentMessages(Long chatSessionId, int limit) {
        List<ChatContent> recentDesc = chatContentRepository
                .findByChatSession_IdOrderByIdDesc(chatSessionId, PageRequest.of(0, limit));
        Collections.reverse(recentDesc);
        return recentDesc;
    }

    @Transactional
    public ChatContent appendMessage(ChatSession session, ChatRole role, String content) {
        ChatContent saved = chatContentRepository.save(
                ChatContent.builder().chatSession(session).role(role).content(content).build());
        session.touch();
        return saved;
    }

    /***
     * 함수 기능: 대화방의 답변 생성을 선점한다. 같은 대화방에서 이미 답변을 생성 중이면 예외를 던져
     *          질문/답변 순서가 섞이지 않도록 한다. 다른 대화방의 요청에는 영향을 주지 않는다.
     */
    @Transactional
    public void acquireAnswering(Long chatSessionId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = chatSessionRepository.tryAcquireAnswering(
                chatSessionId, now, now.minus(ANSWERING_STALE_TIMEOUT));
        if (updated == 0) {
            throw new ChatException(ChatErrorCode.ANSWER_IN_PROGRESS);
        }
    }

    // 답변 생성 선점 해제. 성공/실패와 관계없이 답변 처리가 끝나면 호출한다.
    @Transactional
    public void releaseAnswering(Long chatSessionId) {
        chatSessionRepository.releaseAnswering(chatSessionId);
    }

    private ChatSession getOwnedSession(Long memberId, Long chatSessionId) {
        ChatSession session = chatSessionRepository.findById(chatSessionId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.SESSION_NOT_FOUND));
        if (!session.getMember().getId().equals(memberId)) {
            throw new ChatException(ChatErrorCode.SESSION_FORBIDDEN);
        }
        return session;
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    /***
     * 함수 기능: 대화방 제목의 공백을 정리하고 길이를 제한한다.
     * @param autoGenerate true면 질문 문장을 잘라 자동 생성하는 경우(길면 말줄임표 추가),
     *                      false면 사용자가 직접 입력한 제목을 정리하는 경우
     */
    private String normalizeTitle(String rawTitle, boolean autoGenerate) {
        String normalized = rawTitle == null ? "" : rawTitle.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return DEFAULT_TITLE;
        }
        if (normalized.length() <= MAX_TITLE_LENGTH) {
            return normalized;
        }
        String truncated = normalized.substring(0, MAX_TITLE_LENGTH);
        return autoGenerate ? truncated + "…" : truncated;
    }
}