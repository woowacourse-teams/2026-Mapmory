package com.mapmory.backend.sharevideo.worker;

import com.mapmory.backend.sharevideo.ShareVideoJob;
import com.mapmory.backend.sharevideo.ShareVideoJobRepository;
import com.mapmory.backend.sharevideo.storage.ShareVideoStorage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareVideoJobClaimer {

    private final ShareVideoJobRepository shareVideoJobRepository;
    private final Clock clock;
    private final ShareVideoStorage storage;

    public ShareVideoJobClaimer(
            final ShareVideoJobRepository shareVideoJobRepository,
            final Clock clock,
            final ShareVideoStorage storage
    ) {
        this.shareVideoJobRepository = shareVideoJobRepository;
        this.clock = clock.withZone(ZoneOffset.UTC);
        this.storage = storage;
    }

    @Transactional
    public Optional<ShareVideoRenderTask> claimNext() {
        return shareVideoJobRepository
                .findNextPendingForUpdate()
                .map(job -> startRendering(job, LocalDateTime.now(clock)));
    }

    private ShareVideoRenderTask startRendering(
            final ShareVideoJob job,
            final LocalDateTime startedAt
    ) {
        String claimToken = UUID.randomUUID().toString();
        String attemptId = UUID.randomUUID().toString();
        String objectKey = storage.attemptObjectKey(job.getId(), attemptId);
        job.claimAttempt(claimToken, attemptId, objectKey, startedAt, startedAt.plusMinutes(5));

        return new ShareVideoRenderTask(
                job.getId(),
                job.getStyleId(),
                job.getSnapshotJson(),
                claimToken,
                objectKey
        );
    }
}
