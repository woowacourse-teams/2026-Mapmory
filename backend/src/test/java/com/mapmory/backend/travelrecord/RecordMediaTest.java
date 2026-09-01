package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class RecordMediaTest {

    @Test
    void 썸네일_키가_있으면_썸네일_객체_키를_반환한다() {
        RecordMedia media = RecordMedia.of(
                mock(TravelRecord.class),
                "mapmory/original.jpg",
                "mapmory/thumbnail.jpg",
                0
        );

        assertThat(media.getThumbnailObjectKey()).isEqualTo("mapmory/thumbnail.jpg");
    }

    @Test
    void 썸네일_키가_없으면_원본_객체_키를_반환한다() {
        RecordMedia media = RecordMedia.of(
                mock(TravelRecord.class),
                "mapmory/original.jpg",
                null,
                0
        );

        assertThat(media.getThumbnailObjectKey()).isEqualTo("mapmory/original.jpg");
    }
}
