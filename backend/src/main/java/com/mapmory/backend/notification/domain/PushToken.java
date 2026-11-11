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
import java.util.Objects;

/**
 * 회원이 쓰는 기기의 FCM 등록 토큰. 회원은 id로만 가리킨다.
 */
@Entity
@Table(name = "push_token")
public class PushToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PushPlatform platform;

    @Column(name = "photo_record_completion_enabled", nullable = false)
    private boolean photoRecordCompletionEnabled = true;

    @Column(name = "travel_video_completion_enabled", nullable = false)
    private boolean travelVideoCompletionEnabled = true;

    public void updateDevice(String token, PushPlatform platform, boolean photoEnabled, boolean videoEnabled) {
        this.token = Objects.requireNonNull(token, "token must not be null");
        this.platform = Objects.requireNonNull(platform, "platform must not be null");
        this.photoRecordCompletionEnabled = photoEnabled;
        this.travelVideoCompletionEnabled = videoEnabled;
    }

    public boolean allowsCompletion(String eventType) {
        return switch (eventType) {
            case "PHOTO_RECORD_COMPLETED" -> photoRecordCompletionEnabled;
            case "VIDEO_COMPLETED" -> travelVideoCompletionEnabled;
            default -> true;
        };
    }

    protected PushToken() {
    }

    public PushToken(Long memberId, String token, PushPlatform platform) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.token = Objects.requireNonNull(token, "token must not be null");
        this.platform = Objects.requireNonNull(platform, "platform must not be null");
    }

    /**
     * 등록의 소유 회원과 플랫폼을 갱신한다.
     * 계정 전환 시에는 서비스가 기존 행을 삭제하고 새로 등록해 이전 ID를 재사용하지 않는다.
     */
    public void assignTo(Long memberId, PushPlatform platform) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.platform = Objects.requireNonNull(platform, "platform must not be null");
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getToken() {
        return token;
    }

    public PushPlatform getPlatform() {
        return platform;
    }
}
