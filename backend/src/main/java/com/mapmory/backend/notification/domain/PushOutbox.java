package com.mapmory.backend.notification.domain;

import com.mapmory.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 보낼 알림 하나. 앨범 쓰기와 같은 트랜잭션에 적고, 스케줄러가 꺼내 보낸다(ADR 0022 2026-10-29 보완: 아웃박스).
 *
 * <p>처리 중 상태를 따로 두지 않는다. 집어 갈 때 {@link #lease}로 다음 시도 시각을 미뤄 두면,
 * 보내다 서버가 죽어도 그 시각이 지나 다시 집힌다. 그래서 같은 알림이 두 번 갈 수 있다(안 가는 것보다 낫다).
 */
@Entity
@Table(name = "push_outbox")
public class PushOutbox extends BaseEntity {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LEASE = Duration.ofMinutes(5);
    // n번째 실패 뒤에 기다릴 시간. 5번째 실패면 포기한다.
    private static final List<Duration> BACKOFFS =
            List.of(Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(30));
    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PushOutboxType type;

    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PushOutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    protected PushOutbox() {
    }

    private PushOutbox(PushOutboxType type, String payload, LocalDateTime now) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.status = PushOutboxStatus.PENDING;
        this.nextAttemptAt = Objects.requireNonNull(now, "now must not be null");
    }

    public static PushOutbox pending(PushOutboxType type, String payload, LocalDateTime now) {
        return new PushOutbox(type, payload, now);
    }

    /**
     * 이 서버가 보내는 동안 다른 서버나 다음 차례가 집지 않게 다음 시도를 미룬다.
     */
    public void lease(LocalDateTime now) {
        this.nextAttemptAt = now.plus(LEASE);
    }

    public void sent() {
        this.status = PushOutboxStatus.SENT;
    }

    /**
     * 실패를 센다. 다섯 번째 실패면 포기하고, 아니면 30초·2분·10분·30분 뒤에 다시 보낸다.
     */
    public void failed(LocalDateTime now, String reason) {
        this.attempts++;
        this.lastError = reason == null || reason.length() <= MAX_ERROR_LENGTH
                ? reason : reason.substring(0, MAX_ERROR_LENGTH);
        if (attempts >= MAX_ATTEMPTS) {
            this.status = PushOutboxStatus.FAILED;
            return;
        }
        this.nextAttemptAt = now.plus(BACKOFFS.get(attempts - 1));
    }

    public boolean isGivenUp() {
        return status == PushOutboxStatus.FAILED;
    }

    public Long getId() {
        return id;
    }

    public PushOutboxType getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public PushOutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }
}
