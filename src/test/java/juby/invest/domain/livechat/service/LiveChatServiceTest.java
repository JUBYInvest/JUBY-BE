package juby.invest.domain.livechat.service;

import juby.invest.domain.livechat.dto.LiveChatResDto;
import juby.invest.domain.livechat.entity.LiveChatMessage;
import juby.invest.domain.livechat.exception.LiveChatException;
import juby.invest.domain.livechat.exception.code.LiveChatErrorCode;
import juby.invest.domain.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/***
 * 보관 한도 정리는 "경계 id를 구해 그 이하를 지운다"는 쿼리 동작이 핵심이라 H2 위에서 서비스를 그대로 돌린다.
 * 전송 간격 제한은 여기서는 항상 통과시키고(목), 시간 계산은 LiveChatRateLimiterTest에서 따로 확인한다.
 */
@DataJpaTest
@Import(LiveChatService.class)
@DisplayName("LiveChatService")
class LiveChatServiceTest {

    @Autowired private TestEntityManager em;
    @Autowired private LiveChatService liveChatService;
    @MockitoBean private LiveChatRateLimiter rateLimiter;

    private Member writer;
    private Member noNickname;

    @BeforeEach
    void setUp() {
        writer = em.persist(Member.builder().name("작성자").nickname("차트독학").build());
        noNickname = em.persist(Member.builder().name("닉네임없음").build());
        em.flush();
        given(rateLimiter.tryAcquire(anyLong())).willReturn(true);
    }

    private LiveChatErrorCode errorCodeOf(Throwable e) {
        return (LiveChatErrorCode) ((LiveChatException) e).getErrorCode();
    }

    private long storedCount() {
        return em.getEntityManager().createQuery("select count(m) from LiveChatMessage m", Long.class).getSingleResult();
    }

    @Nested
    @DisplayName("전송")
    class Send {

        @Test
        @DisplayName("저장하고 닉네임과 앞뒤 공백을 뺀 내용을 돌려준다")
        void savesMessage() {
            LiveChatResDto.Message res = liveChatService.send(writer.getId(), "  MDD가 최대 낙폭 맞나요?  ");

            assertThat(res.messageId()).isNotNull();
            assertThat(res.writerNickname()).isEqualTo("차트독학");
            assertThat(res.content()).isEqualTo("MDD가 최대 낙폭 맞나요?");
            assertThat(res.createdAt()).isNotNull();
            assertThat(storedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("빈 메시지나 공백만 있는 메시지는 보낼 수 없다")
        void rejectsBlank() {
            assertThatThrownBy(() -> liveChatService.send(writer.getId(), "   "))
                    .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(LiveChatErrorCode.EMPTY_MESSAGE));
            assertThatThrownBy(() -> liveChatService.send(writer.getId(), null))
                    .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(LiveChatErrorCode.EMPTY_MESSAGE));
        }

        @Test
        @DisplayName("300자까지 보낼 수 있고 301자부터는 거부한다")
        void lengthLimit() {
            liveChatService.send(writer.getId(), "가".repeat(LiveChatService.MAX_CONTENT_LENGTH));

            assertThatThrownBy(() -> liveChatService.send(writer.getId(), "가".repeat(LiveChatService.MAX_CONTENT_LENGTH + 1)))
                    .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(LiveChatErrorCode.MESSAGE_TOO_LONG));
        }

        @Test
        @DisplayName("닉네임이 없으면 보낼 수 없다")
        void requiresNickname() {
            assertThatThrownBy(() -> liveChatService.send(noNickname.getId(), "안녕하세요"))
                    .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(LiveChatErrorCode.NICKNAME_REQUIRED));
            assertThat(storedCount()).isZero();
        }

        @Test
        @DisplayName("전송 간격 제한에 걸리면 저장하지 않는다")
        void rejectsWhenRateLimited() {
            given(rateLimiter.tryAcquire(writer.getId())).willReturn(false);

            assertThatThrownBy(() -> liveChatService.send(writer.getId(), "안녕하세요"))
                    .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(LiveChatErrorCode.TOO_MANY_MESSAGES));
            assertThat(storedCount()).isZero();
        }

        @Test
        @DisplayName("300개를 넘으면 가장 오래된 메시지부터 지워 최근 300개만 남긴다")
        void keepsOnlyLatestMessages() {
            int total = LiveChatService.MAX_STORED_MESSAGES + 5;
            for (int i = 1; i <= total; i++) {
                liveChatService.send(writer.getId(), "메시지 " + i);
            }
            em.clear();

            assertThat(storedCount()).isEqualTo(LiveChatService.MAX_STORED_MESSAGES);

            List<String> contents = em.getEntityManager()
                    .createQuery("select m from LiveChatMessage m order by m.id", LiveChatMessage.class)
                    .getResultList().stream().map(LiveChatMessage::getContent).toList();
            assertThat(contents.get(0)).isEqualTo("메시지 6");
            assertThat(contents.get(contents.size() - 1)).isEqualTo("메시지 " + total);
        }
    }

    @Nested
    @DisplayName("이전 대화 조회")
    class GetMessages {

        @Test
        @DisplayName("최근 size개를 오래된 순으로 주고, nextCursor로 그보다 이전 대화를 이어 본다")
        void pagesBackwards() {
            for (int i = 1; i <= 5; i++) {
                liveChatService.send(writer.getId(), "메시지 " + i);
            }

            LiveChatResDto.MessageList first = liveChatService.getMessages(null, 2);
            assertThat(first.messages()).extracting(LiveChatResDto.Message::content)
                    .containsExactly("메시지 4", "메시지 5");
            assertThat(first.hasNext()).isTrue();
            assertThat(first.nextCursor()).isEqualTo(first.messages().get(0).messageId());

            LiveChatResDto.MessageList second = liveChatService.getMessages(first.nextCursor(), 2);
            assertThat(second.messages()).extracting(LiveChatResDto.Message::content)
                    .containsExactly("메시지 2", "메시지 3");

            LiveChatResDto.MessageList last = liveChatService.getMessages(second.nextCursor(), 2);
            assertThat(last.messages()).extracting(LiveChatResDto.Message::content)
                    .containsExactly("메시지 1");
            assertThat(last.hasNext()).isFalse();
            assertThat(last.nextCursor()).isNull();
        }

        @Test
        @DisplayName("대화가 없으면 빈 목록을 준다")
        void empty() {
            LiveChatResDto.MessageList res = liveChatService.getMessages(null, 50);

            assertThat(res.messages()).isEmpty();
            assertThat(res.hasNext()).isFalse();
            assertThat(res.nextCursor()).isNull();
        }
    }
}
