package com.mapmory.backend.sharevideo.worker;

import com.mapmory.backend.sharevideo.ShareVideoJob;
import com.mapmory.backend.sharevideo.ShareVideoJobRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareVideoJobClaimer {

    private final ShareVideoJobRepository shareVideoJobRepository;
    private final Clock clock;

    public ShareVideoJobClaimer(
            final ShareVideoJobRepository shareVideoJobRepository,
            final Clock clock
    ) {
        this.shareVideoJobRepository = shareVideoJobRepository;
        this.clock = clock.withZone(ZoneOffset.UTC);
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
        job.startRendering(startedAt);

        return new ShareVideoRenderTask(
                job.getId(),
                job.getSnapshotJson()
        );
    }
}
