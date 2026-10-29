package com.mapmory.backend.sharevideo.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.sharevideo.ShareVideoJob;
import com.mapmory.backend.sharevideo.ShareVideoJobRepository;
import com.mapmory.backend.sharevideo.ShareVideoFailureCode;
import com.mapmory.backend.sharevideo.ShareVideoStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShareVideoJobUpdaterTest {

    private final ShareVideoJobRepository shareVideoJobRepository = mock(ShareVideoJobRepository.class);
    private final ShareVideoNotificationOutboxWriter notificationOutboxWriter =
            mock(ShareVideoNotificationOutboxWriter.class);
    private final ShareVideoJobUpdater jobUpdater = new ShareVideoJobUpdater(
            shareVideoJobRepository,
            notificationOutboxWriter,
            Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    );

    @Test
    void 영상_생성을_완료하면_결과_Object_Key를_저장한다() {
        ShareVideoJob job = renderingJob();
        when(shareVideoJobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));

        jobUpdater.complete(1L, "share-videos/1/travel-timeline.mp4", 1_500L, 250L);

        assertThat(job.getStatus()).isEqualTo(ShareVideoStatus.COMPLETED);
        assertThat(job.getResultObjectKey()).isEqualTo("share-videos/1/travel-timeline.mp4");
        assertThat(job.getFailureReason()).isNull();
        assertThat(job.getFinishedAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 3, 0));
    }

    @Test
    void 영상_생성에_실패하면_실패_이유를_저장한다() {
        ShareVideoJob job = renderingJob();
        when(shareVideoJobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));

        jobUpdater.fail(
                1L,
                ShareVideoFailureCode.RENDER_FAILED,
                new IllegalStateException("렌더링 실패"),
                1_500L,
                null
        );

        assertThat(job.getStatus()).isEqualTo(ShareVideoStatus.FAILED);
        assertThat(job.getFailureReason()).isEqualTo("렌더링 실패");
        assertThat(job.getFinishedAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 3, 0));
    }

    @Test
    void 예외_메시지가_없으면_예외_클래스명을_실패_이유로_저장한다() {
        ShareVideoJob job = renderingJob();
        when(shareVideoJobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));

        jobUpdater.fail(1L, ShareVideoFailureCode.RENDER_FAILED, new IllegalStateException(), 1_500L, null);

        assertThat(job.getFailureReason()).isEqualTo("IllegalStateException");
    }

    @Test
    void 실패_이유는_DB_컬럼_길이인_천_자로_제한한다() {
        ShareVideoJob job = renderingJob();
        when(shareVideoJobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));

        jobUpdater.fail(
                1L,
                ShareVideoFailureCode.RENDER_FAILED,
                new IllegalStateException("가".repeat(1_001)),
                1_500L,
                null
        );

        assertThat(job.getFailureReason()).hasSize(1_000);
    }

    @Test
    void 존재하지_않는_작업은_상태를_변경하지_않는다() {
        when(shareVideoJobRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobUpdater.complete(999L, "object-key", 1_500L, 250L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jobId=999");
    }

    private ShareVideoJob renderingJob() {
        ShareVideoJob job = ShareVideoJob.pending(
                mock(Member.class),
                "TRAVEL_TIMELINE",
                "{\"version\":1}"
        );
        job.startRendering(LocalDateTime.now(Clock.systemUTC()));
        return job;
    }
}
