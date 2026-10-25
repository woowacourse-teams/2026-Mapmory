package com.mapmory.backend.upload.policy;

import com.mapmory.backend.upload.domain.UploadFileType;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 업로드 객체의 키를 만든다.
 *
 * 형식: {keyPrefix}/travel-records/{memberId}/{UUID}.{ext}
 *
 * 회원별로 묶여 있어 회원 단위 정리가 가능하고, 파일명이 UUID라 다른 사용자가 추측할 수 없다.
 * keyPrefix는 공용 버킷에서 우리 객체를 한 곳에 모으기 위한 값으로, 없으면 붙이지 않는다.
 */
@Component
public class ObjectKeyGenerator {

    private static final String PREFIX = "travel-records";

    private final String keyPrefix;

    public ObjectKeyGenerator(S3StorageProperties properties) {
        this.keyPrefix = properties.normalizedKeyPrefix();
    }

    public String generate(Long memberId, UploadFileType fileType) {
        String objectKey = "%s/%d/%s.%s".formatted(
                PREFIX,
                memberId,
                UUID.randomUUID(),
                fileType.canonicalExtension()
        );
        if (keyPrefix.isEmpty()) {
            return objectKey;
        }
        return keyPrefix + "/" + objectKey;
    }
}
