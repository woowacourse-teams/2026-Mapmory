package com.mapmory.backend.group.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mapmory.backend.group.application.AlbumEntryCleaner;
import com.mapmory.backend.travelrecord.api.TravelRecordDeleted;
import org.junit.jupiter.api.Test;

class TravelRecordDeletedListenerTest {

    private final AlbumEntryCleaner cleaner = mock(AlbumEntryCleaner.class);
    private final TravelRecordDeletedListener listener = new TravelRecordDeletedListener(cleaner);

    @Test
    void 지워진_기록의_항목을_뗀다() {
        listener.onTravelRecordDeleted(new TravelRecordDeleted(101L, 1L));

        verify(cleaner).removeRecord(101L);
    }

    @Test
    void 떼다가_실패해도_기록_삭제에_예외를_올리지_않는다() {
        willThrow(new IllegalStateException("db down")).given(cleaner).removeRecord(101L);

        assertThatCode(() -> listener.onTravelRecordDeleted(new TravelRecordDeleted(101L, 1L)))
                .doesNotThrowAnyException();
    }
}
