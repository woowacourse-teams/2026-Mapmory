package com.mapmory.backend.sharevideo.worker;

import com.mapmory.backend.sharevideo.ShareVideoJobRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareVideoLeaseService {

    private final ShareVideoJobRepository jobRepository;
    private final Clock clock;

    public ShareVideoLeaseService(final ShareVideoJobRepository jobRepository, final Clock clock) {
        this.jobRepository = jobRepository;
        this.clock = clock.withZone(ZoneOffset.UTC);
    }

    @Transactional
    public boolean renew(final Long jobId, final String claimToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        return jobRepository.findByIdForUpdate(jobId)
                .map(job -> job.renewLease(claimToken, now, now.plusMinutes(5)))
                .orElse(false);
    }
}
