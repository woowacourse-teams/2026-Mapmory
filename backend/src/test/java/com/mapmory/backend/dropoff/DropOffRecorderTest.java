package com.mapmory.backend.dropoff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DropOffRecorderTest {

    private final DropOffLogRepository repository = mock(DropOffLogRepository.class);
    private final DropOffRecorder recorder = new DropOffRecorder(repository,
            Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void 같은_회원이_같은_오류로_거듭_막히면_한_번만_남긴다() {
        DropOffContext context = new DropOffContext(1L, null);

        for (int i = 0; i < 5; i++) {
            recorder.uploadRejected(context, 100, 1024L, "image/jpeg", "UPLOAD_RATE_LIMITED");
        }

        verify(repository, times(1)).save(any());
    }

    @Test
    void 다른_오류나_다른_회원은_따로_남긴다() {
        recorder.uploadRejected(new DropOffContext(1L, null), 1, 1024L, "image/jpeg", "UPLOAD_RATE_LIMITED");
        recorder.uploadRejected(new DropOffContext(1L, null), 1, 20_000_000L, "image/heic", "FILE_SIZE_EXCEEDED");
        recorder.uploadRejected(new DropOffContext(2L, null), 1, 1024L, "image/jpeg", "UPLOAD_RATE_LIMITED");

        verify(repository, times(3)).save(any());
    }

    @Test
    void 성공한_검색은_거듭돼도_모두_남긴다() {
        DropOffContext context = new DropOffContext(1L, null);

        recorder.searched(context, "s", "대천", 0);
        recorder.searched(context, "s", "대천해", 0);

        verify(repository, times(2)).save(any());
    }

    @Test
    void 기록을_남기지_못해도_예외를_던지지_않는다() {
        when(repository.save(any())).thenThrow(new IllegalStateException("DB 끊김"));

        recorder.searched(new DropOffContext(1L, null), "s", "대천", 0);
    }
}
