package com.mapmory.backend.sharevideo.worker;

import com.mapmory.backend.sharevideo.ShareVideoJob;
import com.mapmory.backend.sharevideo.ShareVideoFailureCode;
import com.mapmory.backend.sharevideo.ShareVideoJobRepository;
import com.mapmory.backend.sharevideo.ShareVideoStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareVideoJobUpdater {

    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final ShareVideoJobRepository shareVideoJobRepository;
    private final ShareVideoNotificationOutboxWriter notificationOutboxWriter;
    private final Clock clock;

    public ShareVideoJobUpdater(
            final ShareVideoJobRepository shareVideoJobRepository,
            final ShareVideoNotificationOutboxWriter notificationOutboxWriter,
            final Clock clock
    ) {
        this.shareVideoJobRepository = shareVideoJobRepository;
        this.notificationOutboxWriter = notificationOutboxWriter;
        this.clock = clock.withZone(ZoneOffset.UTC);
    }

    @Transactional
    public void complete(
            final Long jobId,
            final String resultObjectKey,
            final Long renderDurationMs,
            final Long uploadDurationMs
    ) {
        ShareVideoJob job = shareVideoJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalStateException("영상 작업을 찾을 수 없습니다. jobId=" + jobId));

        if (job.getStatus() == ShareVideoStatus.COMPLETED) {
            if (!Objects.equals(job.getResultObjectKey(), resultObjectKey)) {
                throw new IllegalStateException("이미 완료된 영상의 결과 경로가 다릅니다. jobId=" + jobId);
            }
            return;
        }

        if (job.getStatus() != ShareVideoStatus.RENDERING) {
            throw new IllegalStateException("생성 중인 영상만 완료할 수 있습니다. jobId=" + jobId);
        }
        LocalDateTime nowUtc = LocalDateTime.now(clock);

        // 영상 작업을 완료 상태로 변경
        job.complete(
                resultObjectKey,
                nowUtc,
                renderDurationMs,
                uploadDurationMs
        );

        // 같은 트랜잭션에서 알림 발송 기록도 저장
        notificationOutboxWriter.save(
                job.getId(),
                job.getMemberId(),
                job.getRecipientPushTokenId(),
                nowUtc
        );
    }

    @Transactional
    public void fail(
            final Long jobId,
            final ShareVideoFailureCode failureCode,
            final Throwable throwable,
            final Long renderDurationMs,
            final Long uploadDurationMs
    ) {
        ShareVideoJob job = shareVideoJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalStateException("영상 작업을 찾을 수 없습니다. jobId=" + jobId));

        if (job.getStatus() == ShareVideoStatus.COMPLETED || job.getStatus() == ShareVideoStatus.FAILED) {
            return;
        }

        if (job.getStatus() != ShareVideoStatus.RENDERING) {
            throw new IllegalStateException("생성 중인 영상만 실패 처리할 수 있습니다. jobId=" + jobId);
        }
        job.fail(
                failureCode,
                failureReason(throwable),
                LocalDateTime.now(clock),
                renderDurationMs,
                uploadDurationMs
        );
    }

    private String failureReason(final Throwable throwable) {
        String message = throwable.getMessage();

        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }

        if (message.length() <= MAX_FAILURE_REASON_LENGTH) {
            return message;
        }

        return message.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
