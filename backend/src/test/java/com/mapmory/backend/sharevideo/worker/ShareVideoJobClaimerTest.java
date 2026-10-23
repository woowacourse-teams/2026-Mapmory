package com.mapmory.backend.sharevideo.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.sharevideo.ShareVideoJob;
import com.mapmory.backend.sharevideo.ShareVideoJobRepository;
import com.mapmory.backend.sharevideo.ShareVideoStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ShareVideoJobClaimerTest {

    private final ShareVideoJobRepository shareVideoJobRepository = mock(ShareVideoJobRepository.class);
    private final ShareVideoJobClaimer jobClaimer = new ShareVideoJobClaimer(shareVideoJobRepository);

    @Test
    void 대기_작업을_선점하고_렌더링에_필요한_값을_반환한다() {
        ShareVideoJob job = ShareVideoJob.pending(
                mock(Member.class),
                "TRAVEL_TIMELINE",
                "{\"version\":1}"
        );
        ReflectionTestUtils.setField(job, "id", 15L);
        when(shareVideoJobRepository.findNextPendingForUpdate()).thenReturn(Optional.of(job));

        Optional<ShareVideoRenderTask> result = jobClaimer.claimNext();

        assertThat(result).contains(new ShareVideoRenderTask(15L, "{\"version\":1}"));
        assertThat(job.getStatus()).isEqualTo(ShareVideoStatus.RENDERING);
    }

    @Test
    void 대기_작업이_없으면_빈_값을_반환한다() {
        when(shareVideoJobRepository.findNextPendingForUpdate()).thenReturn(Optional.empty());

        Optional<ShareVideoRenderTask> result = jobClaimer.claimNext();

        assertThat(result).isEmpty();
    }
}
