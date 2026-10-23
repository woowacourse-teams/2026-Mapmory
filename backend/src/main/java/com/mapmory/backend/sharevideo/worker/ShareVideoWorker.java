package com.mapmory.backend.sharevideo.worker;

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

        try {
            log.info(
                    "공유 영상 렌더링을 시작합니다. jobId={}",
                    task.jobId()
            );

            renderedVideo = renderer.render(task);

            String objectKey = storage.upload(
                    task.jobId(),
                    renderedVideo.videoPath()
            );

            jobUpdater.complete(
                    task.jobId(),
                    objectKey
            );

            log.info(
                    "공유 영상 생성을 완료했습니다. jobId={}, objectKey={}",
                    task.jobId(),
                    objectKey
            );
        } catch (Exception exception) {
            log.error(
                    "공유 영상 생성에 실패했습니다. jobId={}",
                    task.jobId(),
                    exception
            );

            markFailed(task.jobId(), exception);
        } finally {
            if (renderedVideo != null) {
                renderer.cleanup(renderedVideo);
            }
        }
    }

    private void markFailed(final Long jobId, final Exception cause) {
        try {
            jobUpdater.fail(jobId, cause);
        } catch (Exception updateException) {
            log.error(
                    "공유 영상 작업의 실패 상태를 저장하지 못했습니다. jobId={}",
                    jobId,
                    updateException
            );
        }
    }
}
