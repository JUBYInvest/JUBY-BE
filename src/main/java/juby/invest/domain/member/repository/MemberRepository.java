package juby.invest.domain.member.repository;

import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.enums.SocialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    // 탈퇴한 회원이 같은 소셜 계정으로 다시 로그인하면 새 회원으로 가입시킨다.
    Optional<Member> findBySocialTypeAndProviderIdAndDeletedAtIsNull(SocialType socialType, String providerId);

    /***
     * 함수 기능: 탈퇴하지 않은 회원만 조회한다.
     *          findById는 탈퇴 행까지 반환하므로 일반 조회에는 이 메서드를 쓴다.
     * @param id 회원 ID
     * @return 활성 회원
     */
    @Query("""
        select m from Member m
        where m.id = :id and m.deletedAt is null
    """)
    Optional<Member> findActiveById(Long id);
}
