package juby.invest.domain.livechat.repository;

import juby.invest.domain.livechat.entity.LiveChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LiveChatMessageRepository extends JpaRepository<LiveChatMessage, Long> {

    /***
     * 함수 기능: cursor보다 오래된 메시지를 최신순으로 조회한다.
     * @param cursor 이 id보다 작은 메시지만 조회 (첫 페이지는 Long.MAX_VALUE)
     * @param pageable 조회 개수 (다음 페이지 여부 판단을 위해 size + 1개를 요청한다)
     */
    @Query("""
        select m from LiveChatMessage m
        join fetch m.member
        where m.id < :cursor
        order by m.id desc
    """)
    List<LiveChatMessage> findBefore(@Param("cursor") Long cursor, Pageable pageable);

    // 최신순 id 목록. 보관 한도를 넘는 첫 메시지의 id를 찾는 데 쓴다.
    @Query("select m.id from LiveChatMessage m order by m.id desc")
    List<Long> findIdsNewestFirst(Pageable pageable);

    /***
     * 함수 기능: id 이하의 메시지를 한 번에 지운다.
     *          MySQL은 같은 테이블을 서브쿼리로 읽으면서 지우는 DELETE를 허용하지 않으므로,
     *          경계 id를 먼저 구하고 이 쿼리로 지운다.
     *          방금 저장한 메시지를 응답에 써야 하므로 영속성 컨텍스트는 비우지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from LiveChatMessage m where m.id <= :id")
    int deleteUpTo(@Param("id") Long id);
}
