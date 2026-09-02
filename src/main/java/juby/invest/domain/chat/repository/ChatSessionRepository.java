package juby.invest.domain.chat.repository;

import juby.invest.domain.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    // 대화방 목록은 최근 대화(수정) 순으로 반환한다.
    List<ChatSession> findByMember_IdOrderByUpdatedAtDesc(Long memberId);
}