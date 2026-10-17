package com.mapmory.backend.servicefeedback;

import com.mapmory.backend.common.entity.BaseEntity;
import com.mapmory.backend.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_feedback")
public class ServiceFeedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ServiceFeedbackCategory category;

    @Column(nullable = false, length = 2000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceFeedbackPlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ServiceFeedbackStatus status;

    protected ServiceFeedback() {
    }

    private ServiceFeedback(Member member, ServiceFeedbackCategory category, String content, ServiceFeedbackPlatform platform) {
        this.member = member;
        this.category = category;
        this.content = content;
        this.platform = platform;
        this.status = ServiceFeedbackStatus.UNCONFIRMED;
    }

    public static ServiceFeedback create(
            Member member,
            ServiceFeedbackCategory category,
            String content,
            ServiceFeedbackPlatform platform
    ) {
        return new ServiceFeedback(member, category, content, platform);
    }

    public void changeStatus(ServiceFeedbackStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public ServiceFeedbackCategory getCategory() {
        return category;
    }

    public String getContent() {
        return content;
    }

    public ServiceFeedbackPlatform getPlatform() {
        return platform;
    }

    public ServiceFeedbackStatus getStatus() {
        return status;
    }
}
