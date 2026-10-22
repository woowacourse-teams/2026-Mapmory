package com.mapmory.backend.sharevideo.worker;

import com.mapmory.backend.sharevideo.ShareVideoJob;
import com.mapmory.backend.sharevideo.ShareVideoJobRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareVideoJobClaimer {

    private final ShareVideoJobRepository shareVideoJobRepository;

    public ShareVideoJobClaimer(final ShareVideoJobRepository shareVideoJobRepository) {
        this.shareVideoJobRepository = shareVideoJobRepository;
    }

    @Transactional
    public Optional<ShareVideoRenderTask> claimNext() {
        return shareVideoJobRepository
                .findNextPendingForUpdate()
                .map(this::startRendering);
    }

    private ShareVideoRenderTask startRendering(final ShareVideoJob job) {
        job.startRendering();

        return new ShareVideoRenderTask(
                job.getId(),
                job.getSnapshotJson()
        );
    }
}
