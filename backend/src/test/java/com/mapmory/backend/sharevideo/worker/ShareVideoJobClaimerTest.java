package com.mapmory.backend.sharevideo.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.sharevideo.ShareVideoJob;
import com.mapmory.backend.sharevideo.ShareVideoJobRepository;
import com.mapmory.backend.sharevideo.ShareVideoStatus;
import com.mapmory.backend.sharevideo.storage.ShareVideoStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ShareVideoJobClaimerTest {

    private final ShareVideoJobRepository shareVideoJobRepository = mock(ShareVideoJobRepository.class);
    private final ShareVideoStorage storage = mock(ShareVideoStorage.class);
    private final ShareVideoJobClaimer jobClaimer = new ShareVideoJobClaimer(
            shareVideoJobRepository,
            Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneId.of("Asia/Seoul")),
            storage
    );

    @Test
    void 대기_작업을_선점하고_렌더링에_필요한_값을_반환한다() {
        ShareVideoJob job = ShareVideoJob.pending(
                mock(Member.class),
                "TRAVEL_TIMELINE",
                "{\"version\":1}"
        );
        ReflectionTestUtils.setField(job, "id", 15L);
        when(shareVideoJobRepository.findNextPendingForUpdate()).thenReturn(Optional.of(job));
        when(storage.attemptObjectKey(org.mockito.ArgumentMatchers.eq(15L), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("attempt.mp4");

        Optional<ShareVideoRenderTask> result = jobClaimer.claimNext();

        assertThat(result).hasValueSatisfying(task -> {
            assertThat(task.jobId()).isEqualTo(15L);
            assertThat(task.snapshotJson()).isEqualTo("{\"version\":1}");
            assertThat(task.claimToken()).isEqualTo(job.getClaimToken());
            assertThat(task.attemptObjectKey()).isEqualTo("attempt.mp4");
        });
        assertThat(job.getStatus()).isEqualTo(ShareVideoStatus.RENDERING);
        assertThat(job.getStartedAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 3, 0));
    }

    @Test
    void 대기_작업이_없으면_빈_값을_반환한다() {
        when(shareVideoJobRepository.findNextPendingForUpdate()).thenReturn(Optional.empty());

        Optional<ShareVideoRenderTask> result = jobClaimer.claimNext();

        assertThat(result).isEmpty();
    }
}
