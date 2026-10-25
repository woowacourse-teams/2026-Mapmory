package com.mapmory.backend.upload.service;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.upload.PresignedUpload;
import com.mapmory.backend.upload.domain.UploadFile;
import com.mapmory.backend.upload.domain.UploadPolicy;
import com.mapmory.backend.upload.storage.ObjectKeyGenerator;
import com.mapmory.backend.upload.storage.PresignedRequest;
import com.mapmory.backend.upload.storage.PresignedUrlProvider;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UploadService {

    private final UploadPolicy uploadPolicy;
    private final ObjectKeyGenerator objectKeyGenerator;
    private final PresignedUrlProvider presignedUrlProvider;
    private final Duration presignedUrlExpiration;

    public UploadService(
            UploadPolicy uploadPolicy,
            ObjectKeyGenerator objectKeyGenerator,
            PresignedUrlProvider presignedUrlProvider,
            S3StorageProperties storageProperties
    ) {
        this.uploadPolicy = uploadPolicy;
        this.objectKeyGenerator = objectKeyGenerator;
        this.presignedUrlProvider = presignedUrlProvider;
        this.presignedUrlExpiration = storageProperties.signedUrlExpiration();
    }

    public List<PresignedUpload> createPresignedUrls(Member member, List<UploadFileCommand> files) {
        uploadPolicy.validateFileCount(files.size());
        List<UploadFile> validatedFiles = files.stream()
                .map(file -> uploadPolicy.validateFile(file.fileName(), file.contentType(), file.fileSize()))
                .toList();

        return validatedFiles.stream()
                .map(file -> createPresignedUpload(member.getId(), file))
                .toList();
    }

    private PresignedUpload createPresignedUpload(Long memberId, UploadFile file) {
        String objectKey = objectKeyGenerator.generate(memberId, file.fileType());
        PresignedRequest presigned = presignedUrlProvider.createPresignedPutUrl(
                objectKey,
                file.contentType(),
                file.size(),
                presignedUrlExpiration
        );
        return new PresignedUpload(
                objectKey,
                presigned.url(),
                presigned.method(),
                file.contentType(),
                presignedUrlExpiration
        );
    }
}
