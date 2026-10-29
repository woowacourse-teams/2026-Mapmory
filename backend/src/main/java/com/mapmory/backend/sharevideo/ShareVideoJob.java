package com.mapmory.backend.sharevideo;

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
import java.time.LocalDateTime;

@Entity
@Table(name = "share_video_job")
public class ShareVideoJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "video_type", nullable = false, length = 30)
    private String videoType;

    @Enumerated(EnumType.STRING)
    @Column(name = "style_id", nullable = false, length = 20)
    private ShareVideoStyle styleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShareVideoStatus status;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "json")
    private String snapshotJson;

    @Column(name = "result_object_key", length = 500)
    private String resultObjectKey;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 50)
    private ShareVideoFailureCode failureCode;

    @Column(name = "render_duration_ms")
    private Long renderDurationMs;

    @Column(name = "upload_duration_ms")
    private Long uploadDurationMs;

    // 요청 당시 등록 ID를 보존한다. 등록이 삭제돼도 영상 작업은 유지한다.
    @Column(name = "recipient_push_token_id", nullable = true)
    private Long recipientPushTokenId;

    // JPA가 엔티티를 조회할 때 사용하는 기본 생성자
    protected ShareVideoJob() {
    }

    private ShareVideoJob(
            final Member member,
            final String videoType,
            final ShareVideoStyle styleId,
            final ShareVideoStatus status,
            final String snapshotJson,
            final Long recipientPushTokenId
    ) {
        this.member = member;
        this.videoType = videoType;
        this.styleId = styleId;
        this.status = status;
        this.snapshotJson = snapshotJson;
        this.recipientPushTokenId = recipientPushTokenId;
    }

    public static ShareVideoJob pending(
            final Member member,
            final String videoType,
            final String snapshotJson
    ) {
        return pending(
                member,
                videoType,
                ShareVideoStyle.defaultStyle(),
                snapshotJson,
                null
        );
    }

    public static ShareVideoJob pending(
            final Member member,
            final String videoType,
            final ShareVideoStyle styleId,
            final String snapshotJson
    ) {
        return pending(member, videoType, styleId, snapshotJson, null);
    }

    public static ShareVideoJob pending(
            final Member member,
            final String videoType,
            final ShareVideoStyle styleId,
            final String snapshotJson,
            final Long recipientPushTokenId
    ) {
        return new ShareVideoJob(
                member,
                videoType,
                styleId,
                ShareVideoStatus.PENDING,
                snapshotJson,
                recipientPushTokenId
        );
    }

    public void startRendering(final LocalDateTime startedAt) {
        status = ShareVideoStatus.RENDERING;
        this.startedAt = startedAt;
    }

    public void complete(
            final String resultObjectKey,
            final LocalDateTime finishedAt,
            final Long renderDurationMs,
            final Long uploadDurationMs
    ) {
        status = ShareVideoStatus.COMPLETED;
        this.resultObjectKey = resultObjectKey;
        failureReason = null;
        failureCode = null;
        this.finishedAt = finishedAt;
        this.renderDurationMs = renderDurationMs;
        this.uploadDurationMs = uploadDurationMs;
    }

    public void fail(
            final ShareVideoFailureCode failureCode,
            final String failureReason,
            final LocalDateTime finishedAt,
            final Long renderDurationMs,
            final Long uploadDurationMs
    ) {
        status = ShareVideoStatus.FAILED;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.finishedAt = finishedAt;
        this.renderDurationMs = renderDurationMs;
        this.uploadDurationMs = uploadDurationMs;
    }

    public Long getId() {
        return id;
    }

    public ShareVideoStatus getStatus() {
        return status;
    }

    public ShareVideoStyle getStyleId() {
        return styleId;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public String getResultObjectKey() {
        return resultObjectKey;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public Long getRecipientPushTokenId() {
        return recipientPushTokenId;
    }

    public ShareVideoFailureCode getFailureCode() {
        return failureCode;
    }
}
