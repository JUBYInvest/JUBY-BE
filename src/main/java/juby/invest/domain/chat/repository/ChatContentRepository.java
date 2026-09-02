package juby.invest.domain.chat.repository;

import juby.invest.domain.chat.entity.ChatContent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatContentRepository extends JpaRepository<ChatContent, Long> {

    // 대화방 상세 조회용: 작성 순(id 오름차순) 전체 메시지
    List<ChatContent> findByChatSession_IdOrderByIdAsc(Long chatSessionId);

    // OpenAI 문맥 구성용: 최신 메시지부터 최대 limit개 (서비스에서 시간순으로 뒤집어 사용)
    List<ChatContent> findByChatSession_IdOrderByIdDesc(Long chatSessionId, Pageable pageable);

    // 대화방 삭제 시 하위 메시지 일괄 삭제
    void deleteByChatSession_Id(Long chatSessionId);
}