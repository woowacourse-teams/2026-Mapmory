package com.mapmory.backend.sharevideo.worker;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import com.mapmory.backend.sharevideo.ShareVideoFailureCode;
import com.mapmory.backend.sharevideo.ShareVideoStyle;
import com.mapmory.backend.sharevideo.render.RenderedShareVideo;
import com.mapmory.backend.sharevideo.render.ShareVideoRenderer;
import com.mapmory.backend.sharevideo.storage.ShareVideoStorage;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.scheduling.TaskScheduler;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

@ExtendWith(MockitoExtension.class)
class ShareVideoWorkerTest {

    private static final ShareVideoRenderTask TASK = new ShareVideoRenderTask(1L, ShareVideoStyle.defaultStyle(), "{\"version\":1}", "claim-token", "result.mp4");
    private static final RenderedShareVideo RENDERED_VIDEO = new RenderedShareVideo(
            Path.of("work/result.mp4"),
            Path.of("work")
    );

    @Mock
    private ShareVideoJobClaimer jobClaimer;

    @Mock
    private ShareVideoJobUpdater jobUpdater;

    @Mock
    private ShareVideoRenderer renderer;

    @Mock
    private ShareVideoStorage storage;

    @Mock
    private ShareVideoLeaseService leaseService;

    @Mock
    private TaskScheduler heartbeatScheduler;

    @InjectMocks
    private ShareVideoWorker worker;

    @BeforeEach
    void setUp() {
        lenient().when(leaseService.renew(1L, "claim-token")).thenReturn(true);
    }

    @Test
    void 대기_작업이_없으면_영상_생성을_시작하지_않는다() {
        when(jobClaimer.claimNext()).thenReturn(Optional.empty());

        worker.processNext();

        verifyNoInteractions(jobUpdater, renderer, storage);
    }

    @Test
    void 렌더링과_업로드를_마치면_완료_상태를_저장하고_임시_파일을_정리한다() {
        when(jobClaimer.claimNext()).thenReturn(Optional.of(TASK));
        when(renderer.render(TASK)).thenReturn(RENDERED_VIDEO);

        worker.processNext();

        InOrder order = inOrder(renderer, storage, jobUpdater);
        order.verify(renderer).render(TASK);
        order.verify(storage).uploadAttempt("result.mp4", RENDERED_VIDEO.videoPath());
        order.verify(jobUpdater).completeClaimed(
                eq(1L),
                eq("claim-token"),
                eq("result.mp4"),
                anyLong(),
                anyLong()
        );
        order.verify(renderer).cleanup(RENDERED_VIDEO);
    }

    @Test
    void 렌더링에_실패하면_실패_상태를_저장하고_업로드하지_않는다() {
        IllegalStateException failure = new IllegalStateException("렌더링 실패");
        when(jobClaimer.claimNext()).thenReturn(Optional.of(TASK));
        when(renderer.render(TASK)).thenThrow(failure);

        worker.processNext();

        verify(jobUpdater).failClaimed(
                eq(1L),
                eq("claim-token"),
                eq(ShareVideoFailureCode.RENDER_FAILED),
                eq(failure),
                anyLong(),
                isNull()
        );
        verifyNoInteractions(storage);
        verify(renderer, never()).cleanup(RENDERED_VIDEO);
    }

    @Test
    void 업로드에_실패해도_실패_상태를_저장하고_임시_파일을_정리한다() {
        IllegalStateException failure = new IllegalStateException("업로드 실패");
        when(jobClaimer.claimNext()).thenReturn(Optional.of(TASK));
        when(renderer.render(TASK)).thenReturn(RENDERED_VIDEO);
        doThrow(failure).when(storage).uploadAttempt("result.mp4", RENDERED_VIDEO.videoPath());

        worker.processNext();

        verify(jobUpdater).failClaimed(
                eq(1L),
                eq("claim-token"),
                eq(ShareVideoFailureCode.UPLOAD_FAILED),
                eq(failure),
                anyLong(),
                anyLong()
        );
        verify(jobUpdater, never()).completeClaimed(
                eq(1L),
                eq("claim-token"),
                eq("result.mp4"),
                anyLong(),
                anyLong()
        );
        verify(renderer).cleanup(RENDERED_VIDEO);
    }

    @Test
    void 일시적인_DB_오류면_완료_기록을_다시_시도한다() {
        when(jobClaimer.claimNext()).thenReturn(Optional.of(TASK));
        when(renderer.render(TASK)).thenReturn(RENDERED_VIDEO);
        doThrow(new CannotAcquireLockException("잠금 충돌"))
                .doNothing()
                .when(jobUpdater).completeClaimed(eq(1L), eq("claim-token"), eq("result.mp4"), anyLong(), anyLong());

        worker.processNext();

        verify(jobUpdater, times(2)).completeClaimed(eq(1L), eq("claim-token"), eq("result.mp4"), anyLong(), anyLong());
        verify(jobUpdater, never()).failClaimed(anyLong(), any(), any(), any(), any(), any());
        verify(renderer).cleanup(RENDERED_VIDEO);
    }

    @Test
    void 일시적인_DB_오류가_계속되면_세_번까지만_시도한다() {
        when(jobClaimer.claimNext()).thenReturn(Optional.of(TASK));
        when(renderer.render(TASK)).thenReturn(RENDERED_VIDEO);
        doThrow(new CannotAcquireLockException("잠금 충돌"))
                .when(jobUpdater).completeClaimed(eq(1L), eq("claim-token"), eq("result.mp4"), anyLong(), anyLong());

        worker.processNext();

        verify(jobUpdater, times(3)).completeClaimed(eq(1L), eq("claim-token"), eq("result.mp4"), anyLong(), anyLong());
        verify(jobUpdater, never()).failClaimed(anyLong(), any(), any(), any(), any(), any());
        verify(renderer).cleanup(RENDERED_VIDEO);
    }

    @Test
    void 일시적이지_않은_완료_기록_오류는_재시도하지_않는다() {
        when(jobClaimer.claimNext()).thenReturn(Optional.of(TASK));
        when(renderer.render(TASK)).thenReturn(RENDERED_VIDEO);
        doThrow(new IllegalStateException("잘못된 상태"))
                .when(jobUpdater).completeClaimed(eq(1L), eq("claim-token"), eq("result.mp4"), anyLong(), anyLong());

        worker.processNext();

        verify(jobUpdater).completeClaimed(eq(1L), eq("claim-token"), eq("result.mp4"), anyLong(), anyLong());
        verify(jobUpdater, never()).failClaimed(anyLong(), any(), any(), any(), any(), any());
        verify(renderer).cleanup(RENDERED_VIDEO);
    }

    @Test
    void 선점이_만료되면_업로드하거나_완료_처리하지_않는다() {
        when(jobClaimer.claimNext()).thenReturn(Optional.of(TASK));
        when(renderer.render(TASK)).thenReturn(RENDERED_VIDEO);
        when(leaseService.renew(1L, "claim-token")).thenReturn(false);

        worker.processNext();

        verifyNoInteractions(storage, jobUpdater);
        verify(renderer).cleanup(RENDERED_VIDEO);
    }

    @Test
    void 작업_중_선점을_연장하고_작업이_끝나면_연장을_중지한다() {
        ScheduledFuture<?> heartbeat = mock(ScheduledFuture.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return heartbeat;
        }).when(heartbeatScheduler).scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));
        when(jobClaimer.claimNext()).thenReturn(Optional.of(TASK));
        when(renderer.render(TASK)).thenReturn(RENDERED_VIDEO);

        worker.processNext();

        verify(leaseService, times(2)).renew(1L, "claim-token");
        verify(heartbeat).cancel(false);
    }
}
