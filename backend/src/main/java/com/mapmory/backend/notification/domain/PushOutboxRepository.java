package com.mapmory.backend.notification.domain;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushOutboxRepository extends JpaRepository<PushOutbox, Long> {

    // 보낼 차례인 알림. 다른 서버가 잡은 행은 건너뛴다. idx_push_outbox_due를 탄다.
    @Query(value = """
            SELECT *
            FROM push_outbox
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PushOutbox> findDueForUpdate(@Param("now") LocalDateTime now, @Param("limit") int limit);

    // 끝난 알림을 지운다. 대기 중인 알림은 지우지 않는다.
    @Modifying
    @Query("DELETE FROM PushOutbox o WHERE o.status <> com.mapmory.backend.notification.domain.PushOutboxStatus.PENDING"
            + " AND o.updatedAt < :before")
    int deleteFinishedBefore(@Param("before") LocalDateTime before);
}
