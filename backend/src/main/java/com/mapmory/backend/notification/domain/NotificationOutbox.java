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
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 알림 유형과 같은 사건의 중복을 구분하는 키
    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "event_key", nullable = false, length = 160)
    private String eventKey;


    // 이벤트 유형별 데이터. 예: {"jobId": 15}
    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    // 요청 당시의 회원과 수신 등록 ID
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "push_token_id", nullable = false)
    private Long pushTokenId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    // 워커가 선점했을 때 사용하는 식별자와 만료 시각
    @Column(name = "claim_token", columnDefinition = "char(36)")
    private String claimToken;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    // FCM이 발송 요청을 접수한 시각
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    protected NotificationOutbox() {
    }

    private NotificationOutbox(
            String eventType,
            String eventKey,
            String payload,
            Long memberId,
            Long pushTokenId,
            LocalDateTime nowUtc
    ) {
        this.eventType = Objects.requireNonNull(eventType);
        this.eventKey = Objects.requireNonNull(eventKey);
        this.payload = Objects.requireNonNull(payload);
        this.memberId = Objects.requireNonNull(memberId);
        this.pushTokenId = Objects.requireNonNull(pushTokenId);

        // 새 이벤트는 즉시 발송 가능한 대기 상태로 시작한다.
        this.status = NotificationOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = Objects.requireNonNull(nowUtc);
    }

    public static NotificationOutbox pending(
            String eventType,
            String eventKey,
            String payload,
            Long memberId,
            Long pushTokenId,
            LocalDateTime nowUtc
    ) {
        return new NotificationOutbox(
                eventType,
                eventKey,
                payload,
                memberId,
                pushTokenId,
                nowUtc
        );
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getPayload() {
        return payload;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getPushTokenId() {
        return pushTokenId;
    }

    public NotificationOutboxStatus getStatus() {
        return status;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void claim(final String claimToken, final LocalDateTime leaseExpiresAt) {
        if (status != NotificationOutboxStatus.PENDING) {
            throw new IllegalStateException("대기 중인 알림만 선점할 수 있습니다.");
        }

        this.status = NotificationOutboxStatus.PROCESSING;
        this.claimToken = Objects.requireNonNull(claimToken);
        this.leaseExpiresAt = Objects.requireNonNull(leaseExpiresAt);
    }

    // 선점 기한이 지난 알림을 다시 대기 상태로
    public void recoverExpiredClaim(final LocalDateTime nowUtc) {
        Objects.requireNonNull(nowUtc);

        if (status != NotificationOutboxStatus.PROCESSING
        || leaseExpiresAt == null || leaseExpiresAt.isAfter(nowUtc)) {
            throw new IllegalStateException("선점 기한이 지난 알림만 복구할 수 있습니다.");
        }

        this.status = NotificationOutboxStatus.PENDING;
        this.claimToken = null;
        this.leaseExpiresAt = null;
        this.nextAttemptAt = nowUtc;
    }
}