package com.mapmory.backend.notification.domain;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT outbox FROM NotificationOutbox outbox WHERE outbox.id = :id")
    Optional<NotificationOutbox> findByIdForUpdate(@Param("id") Long id);

    // 지금 발송할 수 있는 대기 알림을 하나 잠금
    @Query(value = """
            SELECT * FROM notification_outbox
            WHERE status = 'PENDING'
                AND next_attempt_at <= :nowUtc
            ORDER BY next_attempt_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<NotificationOutbox> findNextPendingForUpdate(@Param("nowUtc")LocalDateTime nowUtc);

    @Query(value = """
            SELECT * FROM notification_outbox
            WHERE status = 'PENDING' AND event_type = :eventType AND next_attempt_at <= :nowUtc
            ORDER BY next_attempt_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<NotificationOutbox> findNextPendingForTypeForUpdate(
            @Param("eventType") String eventType, @Param("nowUtc") LocalDateTime nowUtc);

    // 선점 기한이 지난 알림을 하나 잠금
    @Query(value = """
            SELECT *
            FROM notification_outbox
            WHERE status = 'PROCESSING'
              AND lease_expires_at <= :nowUtc
            ORDER BY lease_expires_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<NotificationOutbox> findNextExpiredForUpdate(@Param("nowUtc") LocalDateTime nowUtc);

    @Query(value = """
            SELECT * FROM notification_outbox
            WHERE status = 'PROCESSING' AND event_type = :eventType AND lease_expires_at <= :nowUtc
            ORDER BY lease_expires_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<NotificationOutbox> findNextExpiredForTypeForUpdate(
            @Param("eventType") String eventType, @Param("nowUtc") LocalDateTime nowUtc);
}
