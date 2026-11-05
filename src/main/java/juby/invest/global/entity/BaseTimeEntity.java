package juby.invest.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.LocalDateTime;

/***
 * 생성/수정 시각을 자동으로 기록하는 공통 엔티티.
 * JPA Auditing 대신 엔티티 콜백을 써서 별도 설정 없이 @DataJpaTest에서도 동일하게 동작한다.
 * 주의: JPQL 벌크 UPDATE(좋아요 수 증감 등)는 콜백을 거치지 않으므로 updatedAt이 바뀌지 않는다.
 *       "(수정됨)" 표시가 카운트 변경에 흔들리지 않는 것은 이 덕분이다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 작성 후 수정된 적이 있는지 확인한다.
    public boolean isEdited() {
        return updatedAt != null && createdAt != null && updatedAt.isAfter(createdAt);
    }
}
