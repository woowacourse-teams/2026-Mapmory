package com.mapmory.backend.sharevideo.worker;

import com.mapmory.backend.sharevideo.ShareVideoJob;
import com.mapmory.backend.sharevideo.ShareVideoJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareVideoJobUpdater {

    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final ShareVideoJobRepository shareVideoJobRepository;

    public ShareVideoJobUpdater(final ShareVideoJobRepository shareVideoJobRepository) {
        this.shareVideoJobRepository = shareVideoJobRepository;
    }

    @Transactional
    public void complete(final Long jobId, final String resultObjectKey) {
        ShareVideoJob job = findJob(jobId);
        job.complete(resultObjectKey);
    }

    @Transactional
    public void fail(final Long jobId, final Throwable throwable) {
        ShareVideoJob job = findJob(jobId);
        job.fail(failureReason(throwable));
    }

    private ShareVideoJob findJob(final Long jobId) {
        return shareVideoJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("영상 작업을 찾을 수 없습니다. jobId=" + jobId));
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
