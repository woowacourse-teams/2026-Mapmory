package com.mapmory.backend.sharevideo.worker;

import com.mapmory.backend.sharevideo.ShareVideoFailureCode;
import com.mapmory.backend.sharevideo.render.RenderedShareVideo;
import com.mapmory.backend.sharevideo.render.ShareVideoRenderer;
import com.mapmory.backend.sharevideo.storage.ShareVideoStorage;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    public ShareVideoWorker(
            final ShareVideoJobClaimer jobClaimer,
            final ShareVideoJobUpdater jobUpdater,
            final ShareVideoRenderer renderer,
            final ShareVideoStorage storage
    ) {
        this.jobClaimer = jobClaimer;
        this.jobUpdater = jobUpdater;
        this.renderer = renderer;
        this.storage = storage;
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

        try {
            log.info(
                    "공유 영상 렌더링을 시작합니다. jobId={}",
                    task.jobId()
            );

            failureCode = ShareVideoFailureCode.RENDER_FAILED;
            long renderStartedNanos = System.nanoTime();

            try {
                renderedVideo = renderer.render(task);
            } finally {
                renderDurationMs = elapsedMillis(renderStartedNanos);
            }

            failureCode = ShareVideoFailureCode.UPLOAD_FAILED;
            long uploadStartedNanos = System.nanoTime();
            String objectKey;
            try {
                objectKey = storage.upload(task.jobId(), renderedVideo.videoPath());
            } finally {
                uploadDurationMs = elapsedMillis(uploadStartedNanos);
            }

            failureCode = ShareVideoFailureCode.WORKER_FAILED;
            jobUpdater.complete(
                    task.jobId(),
                    objectKey,
                    renderDurationMs,
                    uploadDurationMs
            );

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

            markFailed(task.jobId(), failureCode, exception, renderDurationMs, uploadDurationMs);
        } finally {
            if (renderedVideo != null) {
                renderer.cleanup(renderedVideo);
            }
        }
    }

    private void markFailed(
            final Long jobId,
            final ShareVideoFailureCode failureCode,
            final Exception cause,
            final Long renderDurationMs,
            final Long uploadDurationMs
    ) {
        try {
            jobUpdater.fail(jobId, failureCode, cause, renderDurationMs, uploadDurationMs);
        } catch (Exception updateException) {
            log.error(
                    "공유 영상 작업의 실패 상태를 저장하지 못했습니다. jobId={}",
                    jobId,
                    updateException
            );
        }
    }

    private long elapsedMillis(final long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
