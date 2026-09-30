package juby.invest.domain.chat.repository;

import juby.invest.domain.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    // 대화방 목록은 최근 대화(수정) 순으로 반환한다.
    List<ChatSession> findByMember_IdOrderByUpdatedAtDesc(Long memberId);

    // 답변 생성 선점: 생성 중이 아니거나(null) 선점 후 staleBefore 이전에 멈춘 경우에만 선점한다.
    // 조건 검사와 변경을 UPDATE 한 번으로 처리해 동시 요청 중 하나만 성공(1 반환)한다.
    @Modifying
    @Query(value = "UPDATE chat_session SET answering_started_at = :now " +
            "WHERE id = :chatSessionId " +
            "AND (answering_started_at IS NULL OR answering_started_at < :staleBefore)",
            nativeQuery = true)
    int tryAcquireAnswering(@Param("chatSessionId") Long chatSessionId,
                            @Param("now") LocalDateTime now,
                            @Param("staleBefore") LocalDateTime staleBefore);

    // 답변 생성 선점 해제
    @Modifying
    @Query(value = "UPDATE chat_session SET answering_started_at = NULL WHERE id = :chatSessionId",
            nativeQuery = true)
    int releaseAnswering(@Param("chatSessionId") Long chatSessionId);
}
