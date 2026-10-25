package com.mapmory.backend.upload.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.upload.UploadErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.unit.DataSize;

class UploadPolicyTest {

    private static final long TEN_MEGABYTES = DataSize.ofMegabytes(10).toBytes();

    private UploadPolicy uploadPolicy;

    @BeforeEach
    void setUp() {
        uploadPolicy = new UploadPolicy(TEN_MEGABYTES, 100);
    }

    @ParameterizedTest
    @CsvSource({
            "photo.jpg, image/jpeg, JPEG",
            "photo.jpeg, image/jpeg, JPEG",
            "photo.png, image/png, PNG",
            "photo.webp, image/webp, WEBP",
            "photo.heic, image/heic, HEIC",
            "photo.jpe, image/jpeg, JPEG",
            "photo.heif, image/heic, HEIC"
    })
    void 허용된_이미지_형식을_검증한다(
            String fileName,
            String contentType,
            UploadFileType expected
    ) {
        assertThat(uploadPolicy.validateFile(fileName, contentType, TEN_MEGABYTES))
                .isEqualTo(new UploadFile(expected, TEN_MEGABYTES));
    }

    @Test
    void 확장자가_없어도_Content_Type으로_이미지_형식을_검증한다() {
        assertThat(uploadPolicy.validateFile("IMG_1234", "image/jpeg", TEN_MEGABYTES).fileType())
                .isEqualTo(UploadFileType.JPEG);
    }

    @Test
    void 허용되지_않은_이미지_형식이면_예외가_발생한다() {
        assertThatThrownBy(() -> uploadPolicy.validateFile("photo.gif", "image/gif", 1L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(UploadErrorCode.INVALID_FILE_TYPE));
    }

    @Test
    void 최대_파일_크기를_초과하면_예외가_발생한다() {
        assertThatThrownBy(() ->
                uploadPolicy.validateFile("photo.jpg", "image/jpeg", TEN_MEGABYTES + 1))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(UploadErrorCode.FILE_SIZE_EXCEEDED));
    }

    @Test
    void 파일_확장자와_MIME이_다르면_예외가_발생한다() {
        assertThatThrownBy(() -> uploadPolicy.validateFile("photo.png", "image/jpeg", 1L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(UploadErrorCode.INVALID_FILE_TYPE));
    }

    @Test
    void 요청당_최대_파일_개수까지_허용한다() {
        uploadPolicy.validateFileCount(100);
    }

    @Test
    void 요청당_최대_파일_개수를_초과하면_예외가_발생한다() {
        assertThatThrownBy(() -> uploadPolicy.validateFileCount(101))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(UploadErrorCode.TOO_MANY_FILES));
    }
}
