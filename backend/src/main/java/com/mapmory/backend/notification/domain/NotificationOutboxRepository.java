package com.mapmory.backend.notification.domain;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO notification_outbox
                (event_type, event_key, payload, member_id, push_token_id, next_attempt_at)
            VALUES (:eventType, :eventKey, :payload, :memberId, :pushTokenId, :nowUtc)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void enqueueOnce(@Param("eventType") String eventType, @Param("eventKey") String eventKey,
            @Param("payload") String payload, @Param("memberId") Long memberId,
            @Param("pushTokenId") Long pushTokenId, @Param("nowUtc") LocalDateTime nowUtc);

    // 회원 데이터 파기.
    @Modifying
    @Transactional
    @Query("DELETE FROM NotificationOutbox outbox WHERE outbox.memberId = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);

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
