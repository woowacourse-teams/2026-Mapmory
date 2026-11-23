package com.mapmory.backend.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DerivedObjectKeysTest {

    private final DerivedObjectKeys keys = new DerivedObjectKeys(properties("mapmory/dev"));

    @ParameterizedTest
    @CsvSource({
            "mapmory/dev/travel-records/7/abc.heic, mapmory/dev/derived/thumb/7/abc.webp",
            "mapmory/dev/travel-records/7/abc.jpg, mapmory/dev/derived/thumb/7/abc.webp",
            "mapmory/dev/travel-records/123/9f1c-uuid.png, mapmory/dev/derived/thumb/123/9f1c-uuid.webp",
    })
    void 원본_키에서_썸네일_키를_만든다(String original, String thumbnail) {
        assertThat(keys.thumbnailOf(original)).contains(thumbnail);
    }

    // 접두사를 붙이기 전에 저장된 옛 키와 낯선 형식은 썸네일을 만들지 않는다. 예외를 던지지 않는다.
    @ParameterizedTest
    @ValueSource(strings = {
            "travel-records/7/abc.jpg",
            "mapmory/prod/travel-records/7/abc.jpg",
            "mapmory/dev/travel-records/7/abc",
            "mapmory/dev/travel-records/abc.jpg",
            "mapmory/dev/travel-records/x/abc.jpg",
            "mapmory/dev/travel-records/7/sub/abc.jpg",
            "mapmory/dev/travel-records/7/.jpg",
            "mapmory/dev/derived/thumb/7/abc.webp",
            "mapmory/dev/travel-records/7/../8/abc.jpg",
            // 서버는 jpg·png·webp·heic만 발급한다. 정리 배치의 역조회(originalCandidatesOf)와 규칙을 맞춘다.
            "mapmory/dev/travel-records/7/abc.jpeg",
            "mapmory/dev/travel-records/7/abc.PNG",
            "mapmory/dev/travel-records/7/abc.gif",
    })
    void 우리_원본_형식이_아니면_빈_값이다(String original) {
        assertThat(keys.thumbnailOf(original)).isEmpty();
    }

    @Test
    void null도_빈_값이다() {
        assertThat(keys.thumbnailOf(null)).isEmpty();
    }

    @Test
    void 파생_경로_아래의_키인지_안다() {
        assertThat(keys.isDerived("mapmory/dev/derived/thumb/7/abc.webp")).isTrue();
        assertThat(keys.isDerived("mapmory/dev/travel-records/7/abc.jpg")).isFalse();
        assertThat(keys.isDerived("mapmory/prod/derived/thumb/7/abc.webp")).isFalse();
        assertThat(keys.isDerived("mapmory/dev/derived/../travel-records/7/abc.jpg")).isFalse();
    }

    // 정리 배치가 썸네일을 지우기 전에, 그 원본이 아직 기록에 붙어 있는지 볼 후보다. 서버가 만드는 확장자만 쓴다.
    @Test
    void 썸네일_키에서_원본_후보_키를_만든다() {
        assertThat(keys.originalCandidatesOf("mapmory/dev/derived/thumb/7/abc.webp")).containsExactlyInAnyOrder(
                "mapmory/dev/travel-records/7/abc.jpg",
                "mapmory/dev/travel-records/7/abc.png",
                "mapmory/dev/travel-records/7/abc.webp",
                "mapmory/dev/travel-records/7/abc.heic");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mapmory/dev/travel-records/7/abc.jpg",
            "mapmory/dev/derived/thumb/7/abc.jpg",
            "mapmory/dev/derived/thumb/7/sub/abc.webp",
            "mapmory/prod/derived/thumb/7/abc.webp",
    })
    void 썸네일_키가_아니면_원본_후보가_없다(String key) {
        assertThat(keys.originalCandidatesOf(key)).isEmpty();
    }

    static S3StorageProperties properties(String keyPrefix) {
        return new S3StorageProperties("bucket", "ap-northeast-2", keyPrefix, Duration.ofMinutes(5),
                new S3StorageProperties.Client(Duration.ofSeconds(1), Duration.ofSeconds(3), 3,
                        Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(30)));
    }
}
