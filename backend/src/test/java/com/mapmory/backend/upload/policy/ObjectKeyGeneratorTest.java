package com.mapmory.backend.upload.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.upload.storage.S3StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectKeyGeneratorTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @ParameterizedTest
    @CsvSource({
            "JPEG, jpg",
            "PNG, png",
            "WEBP, webp",
            "HEIC, heic"
    })
    void 회원과_검증된_파일_형식으로_Object_Key를_생성한다(
            UploadFileType fileType,
            String extension
    ) {
        String objectKey = objectKeyGenerator("").generate(10L, fileType);

        assertThat(objectKey).matches("travel-records/10/" + UUID_PATTERN + "\\." + extension);
    }

    @Test
    void prefix가_설정되면_Object_Key_앞에_붙인다() {
        String objectKey = objectKeyGenerator("mapmory").generate(10L, UploadFileType.JPEG);

        assertThat(objectKey).matches("mapmory/travel-records/10/" + UUID_PATTERN + "\\.jpg");
    }

    /**
     * 운영자가 콘솔의 경로 표기를 그대로 옮겨 적을 수 있으므로 앞뒤 슬래시를 허용한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"mapmory", "/mapmory", "mapmory/", "/mapmory/", " mapmory "})
    void prefix의_앞뒤_슬래시와_공백은_무시한다(String keyPrefix) {
        String objectKey = objectKeyGenerator(keyPrefix).generate(10L, UploadFileType.JPEG);

        assertThat(objectKey).matches("mapmory/travel-records/10/" + UUID_PATTERN + "\\.jpg");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "/"})
    void prefix가_비어_있으면_붙이지_않는다(String keyPrefix) {
        String objectKey = objectKeyGenerator(keyPrefix).generate(10L, UploadFileType.JPEG);

        assertThat(objectKey).startsWith("travel-records/");
    }

    @Test
    void prefix가_설정되지_않아도_동작한다() {
        String objectKey = objectKeyGenerator(null).generate(10L, UploadFileType.JPEG);

        assertThat(objectKey).startsWith("travel-records/");
    }

    private ObjectKeyGenerator objectKeyGenerator(String keyPrefix) {
        return new ObjectKeyGenerator(
                new S3StorageProperties("mapmory-test", "ap-northeast-2", keyPrefix, null));
    }
}
