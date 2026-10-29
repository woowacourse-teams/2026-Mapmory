package com.mapmory.backend.sharevideo.worker;

import com.mapmory.backend.sharevideo.ShareVideoFailureCode;
import com.mapmory.backend.sharevideo.render.RenderedShareVideo;
import com.mapmory.backend.sharevideo.render.ShareVideoRenderer;
import com.mapmory.backend.sharevideo.storage.ShareVideoStorage;
import java.util.Optional;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "share-video.worker",
        name = "enabled",
        havingValue = "true"
)
public class ShareVideoWorker {

    private static final Logger log = LoggerFactory.getLogger(ShareVideoWorker.class);

    private final ShareVideoJobClaimer jobClaimer;
    private final ShareVideoJobUpdater jobUpdater;
    private final ShareVideoRenderer renderer;
    private final ShareVideoStorage storage;
    private final ShareVideoLeaseService leaseService;
    private final TaskScheduler heartbeatScheduler;

    public ShareVideoWorker(
            final ShareVideoJobClaimer jobClaimer,
            final ShareVideoJobUpdater jobUpdater,
            final ShareVideoRenderer renderer,
            final ShareVideoStorage storage,
            final ShareVideoLeaseService leaseService,
            @Qualifier("shareVideoHeartbeatScheduler") final TaskScheduler heartbeatScheduler
    ) {
        this.jobClaimer = jobClaimer;
        this.jobUpdater = jobUpdater;
        this.renderer = renderer;
        this.storage = storage;
        this.leaseService = leaseService;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    @Scheduled(
            fixedDelayString = "${share-video.worker.poll-delay}",
            scheduler = "shareVideoTaskScheduler"
    )
    public void processNext() {
        Optional<ShareVideoRenderTask> nextTask = jobClaimer.claimNext();

        nextTask.ifPresent(this::process);
    }

    private void process(final ShareVideoRenderTask task) {
        RenderedShareVideo renderedVideo = null;
        Long renderDurationMs = null;
        Long uploadDurationMs = null;
        ShareVideoFailureCode failureCode = ShareVideoFailureCode.WORKER_FAILED;
        ScheduledFuture<?> heartbeat = null;

        try {
            heartbeat = heartbeatScheduler.scheduleAtFixedRate(
                    () -> renewHeartbeat(task), Instant.now().plusSeconds(60), Duration.ofMinutes(1));
            log.info(
                    "공유 영상 렌더링을 시작합니다. jobId={}, styleId={}",
                    task.jobId(),
                    task.styleId()
            );

            failureCode = ShareVideoFailureCode.RENDER_FAILED;
            long renderStartedNanos = System.nanoTime();

            try {
                renderedVideo = renderer.render(task);
            } finally {
                renderDurationMs = elapsedMillis(renderStartedNanos);
            }

            try {
                if (!leaseService.renew(task.jobId(), task.claimToken())) {
                    log.warn("영상 작업의 선점이 만료됐습니다. jobId={}", task.jobId());
                    return;
                }
            } catch (Exception exception) {
                log.error("영상 작업의 선점을 확인하지 못했습니다. jobId={}", task.jobId(), exception);
                return;
            }

            failureCode = ShareVideoFailureCode.UPLOAD_FAILED;
            long uploadStartedNanos = System.nanoTime();
            String objectKey;
            try {
                objectKey = task.attemptObjectKey();
                storage.uploadAttempt(objectKey, renderedVideo.videoPath());
            } finally {
                uploadDurationMs = elapsedMillis(uploadStartedNanos);
            }

            boolean completed = completeWithRetry(
                    task.jobId(), task.claimToken(), objectKey, renderDurationMs, uploadDurationMs
            );
            if (!completed) {
                return;
            }

            log.info(
                    "공유 영상 생성을 완료했습니다. jobId={}, renderDurationMs={}, uploadDurationMs={}, objectKey={}",
                    task.jobId(),
                    renderDurationMs,
                    uploadDurationMs,
                    objectKey
            );
        } catch (Exception exception) {
            log.error(
                    "공유 영상 생성에 실패했습니다. jobId={}, failureCode={}, renderDurationMs={}, uploadDurationMs={}",
                    task.jobId(),
                    failureCode,
                    renderDurationMs,
                    uploadDurationMs,
                    exception
            );

            markFailed(task.jobId(), task.claimToken(), failureCode, exception, renderDurationMs, uploadDurationMs);
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
            if (renderedVideo != null) {
                renderer.cleanup(renderedVideo);
            }
        }
    }

    private boolean completeWithRetry(
            final Long jobId,
            final String claimToken,
            final String objectKey,
            final Long renderDurationMs,
            final Long uploadDurationMs
    ) {
        final int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                jobUpdater.completeClaimed(jobId, claimToken, objectKey, renderDurationMs, uploadDurationMs);
                return true;
            } catch (TransientDataAccessException exception) {
                log.warn("영상 완료 기록에 실패했습니다. jobId={}, attempt={}/{}",
                        jobId, attempt, maxAttempts, exception);
            } catch (Exception exception) {
                log.error("영상 완료 기록을 저장하지 못했습니다. jobId={}, objectKey={}",
                        jobId, objectKey, exception);
                return false;
            }
        }

        log.error("영상 완료 기록의 재시도 횟수를 초과했습니다. jobId={}, objectKey={}", jobId, objectKey);
        return false;
    }

    private void markFailed(
            final Long jobId,
            final String claimToken,
            final ShareVideoFailureCode failureCode,
            final Exception cause,
            final Long renderDurationMs,
            final Long uploadDurationMs
    ) {
        try {
            jobUpdater.failClaimed(jobId, claimToken, failureCode, cause, renderDurationMs, uploadDurationMs);
        } catch (Exception updateException) {
            log.error(
                    "공유 영상 작업의 실패 상태를 저장하지 못했습니다. jobId={}",
                    jobId,
                    updateException
            );
        }
    }

    private void renewHeartbeat(final ShareVideoRenderTask task) {
        try {
            if (!leaseService.renew(task.jobId(), task.claimToken())) {
                log.warn("영상 작업의 선점 기한을 연장하지 못했습니다. jobId={}", task.jobId());
            }
        } catch (Exception exception) {
            log.warn("영상 작업의 선점 기한 연장 중 오류가 발생했습니다. jobId={}", task.jobId(), exception);
        }
    }

    private long elapsedMillis(final long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
