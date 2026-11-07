package juby.invest.domain.livechat.service;

import juby.invest.domain.livechat.converter.LiveChatConverter;
import juby.invest.domain.livechat.dto.LiveChatResDto;
import juby.invest.domain.livechat.entity.LiveChatMessage;
import juby.invest.domain.livechat.exception.LiveChatException;
import juby.invest.domain.livechat.exception.code.LiveChatErrorCode;
import juby.invest.domain.livechat.repository.LiveChatMessageRepository;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveChatService {

    // DB에 남겨 두는 최대 메시지 수. 넘으면 오래된 메시지부터 지운다.
    public static final int MAX_STORED_MESSAGES = 300;
    // 메시지 최대 길이. LiveChatMessage.content 컬럼 길이와 같다.
    public static final int MAX_CONTENT_LENGTH = 300;

    private final LiveChatMessageRepository liveChatMessageRepository;
    private final MemberRepository memberRepository;
    private final LiveChatRateLimiter rateLimiter;

    /***
     * 함수 기능: 이전 대화를 커서로 조회한다. 비로그인도 볼 수 있다.
     * @param cursor 이전 응답의 nextCursor (첫 페이지면 null = 가장 최근 대화)
     * @param size 조회 개수
     * @return 오래된 메시지가 먼저 오는 메시지 목록과 다음(더 이전) 커서
     */
    public LiveChatResDto.MessageList getMessages(Long cursor, int size) {

        Long effectiveCursor = cursor == null ? Long.MAX_VALUE : cursor;

        // 한 개를 더 조회해서, 그 한 개가 있으면 더 이전 대화가 있다고 판단한다. (count 쿼리 없이)
        List<LiveChatMessage> newestFirst = liveChatMessageRepository.findBefore(effectiveCursor, PageRequest.ofSize(size + 1));

        boolean hasNext = newestFirst.size() > size;
        List<LiveChatMessage> page = new ArrayList<>(hasNext ? newestFirst.subList(0, size) : newestFirst);

        // 채팅창은 위에서 아래로 오래된 -> 최신 순이므로 뒤집어서 준다.
        Collections.reverse(page);

        return LiveChatConverter.toMessageList(page, hasNext);
    }

    /***
     * 함수 기능: 채팅 메시지를 저장하고, 보관 한도를 넘는 오래된 메시지를 지운다.
     *          브로드캐스트는 호출한 쪽이 커밋 이후에 한다. (저장에 실패한 메시지가 화면에 뿌려지지 않도록)
     * @param memberId 보내는 회원 ID
     * @param rawContent 입력한 메시지
     * @return 저장된 메시지
     */
    @Transactional
    public LiveChatResDto.Message send(Long memberId, String rawContent) {

        String content = validateContent(rawContent);

        // DB를 건드리기 전에 막아, 빠르게 연타해도 쿼리가 늘지 않게 한다.
        if (!rateLimiter.tryAcquire(memberId)) {
            throw new LiveChatException(LiveChatErrorCode.TOO_MANY_MESSAGES);
        }

        Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        // 프론트의 닉네임 팝업은 우회할 수 있으므로 서버에서 다시 막는다.
        if (member.getNickname() == null) {
            throw new LiveChatException(LiveChatErrorCode.NICKNAME_REQUIRED);
        }

        LiveChatMessage saved = liveChatMessageRepository.save(LiveChatMessage.builder()
                .member(member)
                .content(content)
                .build());

        deleteOverflow();

        return LiveChatConverter.toMessage(saved);
    }

    /***
     * 함수 기능: 최신 MAX_STORED_MESSAGES개를 넘는 메시지를 지운다.
     *          (MAX_STORED_MESSAGES + 1)번째로 최신인 메시지를 찾아, 그 id 이하를 모두 지운다.
     *          동시에 두 메시지가 저장돼도 각자 같은 규칙으로 지우므로 결과는 같다.
     */
    private void deleteOverflow() {
        List<Long> boundary = liveChatMessageRepository.findIdsNewestFirst(PageRequest.of(MAX_STORED_MESSAGES, 1));
        if (!boundary.isEmpty()) {
            liveChatMessageRepository.deleteUpTo(boundary.get(0));
        }
    }

    private String validateContent(String rawContent) {
        String content = rawContent == null ? "" : rawContent.strip();

        if (content.isEmpty()) {
            throw new LiveChatException(LiveChatErrorCode.EMPTY_MESSAGE);
        }
        // String.length()는 이모지를 2로 센다. MySQL VARCHAR는 1로 세므로, 이 검사를 통과하면 컬럼에는 항상 들어간다.
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new LiveChatException(LiveChatErrorCode.MESSAGE_TOO_LONG);
        }
        return content;
    }
}
