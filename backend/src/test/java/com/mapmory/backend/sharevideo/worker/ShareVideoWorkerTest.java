package com.mapmory.backend.sharevideo.worker;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import com.mapmory.backend.sharevideo.ShareVideoFailureCode;
import com.mapmory.backend.sharevideo.render.RenderedShareVideo;
import com.mapmory.backend.sharevideo.render.ShareVideoRenderer;
import com.mapmory.backend.sharevideo.storage.ShareVideoStorage;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShareVideoWorkerTest {

    private static final ShareVideoRenderTask TASK = new ShareVideoRenderTask(1L, "{\"version\":1}");
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

    @InjectMocks
    private ShareVideoWorker worker;

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
        when(storage.upload(1L, RENDERED_VIDEO.videoPath()))
                .thenReturn("share-videos/1/travel-timeline.mp4");

        worker.processNext();

        InOrder order = inOrder(renderer, storage, jobUpdater);
        order.verify(renderer).render(TASK);
        order.verify(storage).upload(1L, RENDERED_VIDEO.videoPath());
        order.verify(jobUpdater).complete(
                eq(1L),
                eq("share-videos/1/travel-timeline.mp4"),
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

        verify(jobUpdater).fail(
                eq(1L),
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
        when(storage.upload(1L, RENDERED_VIDEO.videoPath())).thenThrow(failure);

        worker.processNext();

        verify(jobUpdater).fail(
                eq(1L),
                eq(ShareVideoFailureCode.UPLOAD_FAILED),
                eq(failure),
                anyLong(),
                anyLong()
        );
        verify(jobUpdater, never()).complete(
                eq(1L),
                eq("share-videos/1/travel-timeline.mp4"),
                anyLong(),
                anyLong()
        );
        verify(renderer).cleanup(RENDERED_VIDEO);
    }
}
