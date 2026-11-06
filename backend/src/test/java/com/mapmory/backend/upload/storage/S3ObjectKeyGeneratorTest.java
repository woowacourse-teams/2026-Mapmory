package com.mapmory.backend.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.upload.domain.UploadFileType;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class S3ObjectKeyGeneratorTest {

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
        String objectKey = objectKeyGenerator("mapmory").generate(10L, fileType);

        assertThat(objectKey).matches("mapmory/travel-records/10/" + UUID_PATTERN + "\\." + extension);
    }

    @Test
    void 만든_키는_이_서버가_만든_형식이다() {
        S3ObjectKeyGenerator generator = objectKeyGenerator("mapmory/dev");

        assertThat(generator.isGenerated(generator.generate(10L, UploadFileType.HEIC))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mapmory/dev/travel-records/10/",
            "mapmory/dev/travel-records/10/manual.jpg",
            "mapmory/dev/travel-records/backup/3f2504e0-4f89-41d3-9a0c-0305e82c3301.jpg",
            "mapmory/dev/travel-records/10/sub/3f2504e0-4f89-41d3-9a0c-0305e82c3301.jpg",
            "mapmory/dev/travel-records/10/3f2504e0-4f89-41d3-9a0c-0305e82c3301",
            "mapmory/travel-records/10/3f2504e0-4f89-41d3-9a0c-0305e82c3301.jpg",
            "mapmory/dev/share-videos/10/3f2504e0-4f89-41d3-9a0c-0305e82c3301.jpg",
            "mapmory/dev/travel-records/10/3f2504e0-4f89-41d3-9a0c-0305e82c3301.bak",
            "mapmory/dev/travel-records/10/3f2504e0-4f89-41d3-9a0c-0305e82c3301.mp4",
            "mapmory/dev/travel-records/12345678901234567890/3f2504e0-4f89-41d3-9a0c-0305e82c3301.jpg"
    })
    void 서버가_만들지_않는_형식은_아니다(String objectKey) {
        assertThat(objectKeyGenerator("mapmory/dev").isGenerated(objectKey)).isFalse();
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

    @Test
    void 여러_단계의_prefix도_그대로_붙인다() {
        String objectKey = objectKeyGenerator("mapmory/dev").generate(10L, UploadFileType.JPEG);

        assertThat(objectKey).matches("mapmory/dev/travel-records/10/" + UUID_PATTERN + "\\.jpg");
    }

    private S3ObjectKeyGenerator objectKeyGenerator(String keyPrefix) {
        return new S3ObjectKeyGenerator(
                new S3StorageProperties("mapmory-test", "ap-northeast-2", keyPrefix, Duration.ofMinutes(5), null));
    }
}
