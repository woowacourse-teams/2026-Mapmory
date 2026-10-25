package com.mapmory.backend.upload.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UploadFileTypeTest {

    @ParameterizedTest
    @CsvSource({
            "image/jpeg, JPEG",
            "image/png, PNG",
            "image/webp, WEBP",
            "image/heic, HEIC"
    })
    void Content_Type으로_파일_형식을_찾는다(String contentType, UploadFileType expected) {
        assertThat(UploadFileType.findByContentType(contentType)).contains(expected);
    }

    @Test
    void Content_Type은_대소문자와_앞뒤_공백을_정규화한다() {
        assertThat(UploadFileType.findByContentType(" IMAGE/JPEG "))
                .contains(UploadFileType.JPEG);
    }

    @Test
    void 확장자가_없는_파일명은_Content_Type으로_판단한다() {
        assertThat(UploadFileType.JPEG.matchesFileName("IMG_1234")).isTrue();
    }

    @Test
    void 확장자가_있다면_파일_형식과_일치해야_한다() {
        assertThat(UploadFileType.JPEG.matchesFileName("photo.jpeg")).isTrue();
        assertThat(UploadFileType.JPEG.matchesFileName("photo.png")).isFalse();
    }
}
