package com.mapmory.backend.servicefeedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "before_value", length = 50)
    private String beforeValue;

    @Column(name = "after_value", nullable = false, length = 50)
    private String afterValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AdminAuditLog() {
    }

    private AdminAuditLog(String targetType, Long targetId, String beforeValue, String afterValue) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }

    public static AdminAuditLog serviceFeedbackStatus(Long feedbackId, ServiceFeedbackStatus before, ServiceFeedbackStatus after) {
        return new AdminAuditLog("SERVICE_FEEDBACK_STATUS", feedbackId, before.name(), after.name());
    }
}
