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
    @Column(nullable = false, length = 20)
    private ShareVideoStatus status;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "json")
    private String snapshotJson;

    @Column(name = "result_object_key", length = 500)
    private String resultObjectKey;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    protected ShareVideoJob() {
    }

    private ShareVideoJob(
            final Member member,
            final String videoType,
            final ShareVideoStatus status,
            final String snapshotJson
    ) {
        this.member = member;
        this.videoType = videoType;
        this.status = status;
        this.snapshotJson = snapshotJson;
    }

    public static ShareVideoJob pending(
            final Member member,
            final String videoType,
            final String snapshotJson
    ) {
        return new ShareVideoJob(
                member,
                videoType,
                ShareVideoStatus.PENDING,
                snapshotJson
        );
    }

    public void startRendering() {
        status = ShareVideoStatus.RENDERING;
    }

    public void complete(final String resultObjectKey) {
        status = ShareVideoStatus.COMPLETED;
        this.resultObjectKey = resultObjectKey;
        failureReason = null;
    }

    public void fail(final String failureReason) {
        status = ShareVideoStatus.FAILED;
        this.failureReason = failureReason;
    }

    public Long getId() {
        return id;
    }

    public ShareVideoStatus getStatus() {
        return status;
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
}
