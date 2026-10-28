package com.mapmory.backend.recordmedia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecordMediaUrlServiceTest {

    private final MediaViewUrlSigner mediaViewUrlSigner = mock(MediaViewUrlSigner.class);
    private RecordMediaUrlService recordMediaUrlService;

    @BeforeEach
    void setUp() {
        recordMediaUrlService = new RecordMediaUrlService(mediaViewUrlSigner);
    }

    @Test
    void 조회_URL과_만료_시간을_값_객체로_반환한다() {
        when(mediaViewUrlSigner.sign("mapmory/original.jpg"))
                .thenReturn(new ExpiringUrl("https://download.example/mapmory/original.jpg", 300L));

        ExpiringUrl result = recordMediaUrlService.createViewUrl("mapmory/original.jpg");

        assertThat(result).isEqualTo(new ExpiringUrl(
                "https://download.example/mapmory/original.jpg",
                300L
        ));
    }

    @Test
    void 만료_URL은_빈_URL이나_유효하지_않은_만료_시간을_허용하지_않는다() {
        assertThatThrownBy(() -> new ExpiringUrl(" ", 300L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExpiringUrl("https://download.example/file.jpg", 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
