package com.mapmory.backend.upload.storage;

import com.mapmory.backend.upload.domain.UploadFileType;
import com.mapmory.backend.upload.service.port.ObjectKeyGenerator;
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
public class S3ObjectKeyGenerator implements ObjectKeyGenerator {

    private final String travelRecordsDirectory;

    public S3ObjectKeyGenerator(S3StorageProperties properties) {
        this.travelRecordsDirectory = properties.travelRecordsDirectory();
    }

    @Override
    public String generate(Long memberId, UploadFileType fileType) {
        return travelRecordsDirectory + "%d/%s.%s".formatted(
                memberId,
                UUID.randomUUID(),
                fileType.canonicalExtension()
        );
    }

}
