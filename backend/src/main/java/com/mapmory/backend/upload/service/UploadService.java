package com.mapmory.backend.upload.service;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.upload.PresignedUpload;
import com.mapmory.backend.upload.UploadFileCommand;
import com.mapmory.backend.upload.policy.ObjectKeyGenerator;
import com.mapmory.backend.upload.policy.UploadFileType;
import com.mapmory.backend.upload.policy.UploadPolicy;
import com.mapmory.backend.upload.policy.UploadPolicyProperties;
import com.mapmory.backend.upload.storage.PresignedUrlProvider;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UploadService {

    private static final String UPLOAD_METHOD = "PUT";

    private final UploadPolicy uploadPolicy;
    private final ObjectKeyGenerator objectKeyGenerator;
    private final PresignedUrlProvider presignedUrlProvider;
    private final Duration presignedUrlExpiration;

    public UploadService(
            UploadPolicy uploadPolicy,
            ObjectKeyGenerator objectKeyGenerator,
            PresignedUrlProvider presignedUrlProvider,
            UploadPolicyProperties properties
    ) {
        this.uploadPolicy = uploadPolicy;
        this.objectKeyGenerator = objectKeyGenerator;
        this.presignedUrlProvider = presignedUrlProvider;
        this.presignedUrlExpiration = properties.presignedUrlExpiration();
    }

    public List<PresignedUpload> createPresignedUrls(Member member, List<UploadFileCommand> files) {
        uploadPolicy.validateFileCount(files.size());
        List<ValidatedUploadFile> validatedFiles = files.stream()
                .map(file -> new ValidatedUploadFile(
                        file,
                        uploadPolicy.validateFile(file.fileName(), file.contentType(), file.fileSize())
                ))
                .toList();

        return validatedFiles.stream()
                .map(file -> createPresignedUpload(member.getId(), file))
                .toList();
    }

    private PresignedUpload createPresignedUpload(Long memberId, ValidatedUploadFile validatedFile) {
        UploadFileCommand file = validatedFile.file();
        UploadFileType fileType = validatedFile.fileType();
        String objectKey = objectKeyGenerator.generate(memberId, fileType);
        URI presignedUrl = presignedUrlProvider.createPresignedPutUrl(
                objectKey,
                fileType.contentType(),
                file.fileSize(),
                presignedUrlExpiration
        );
        return new PresignedUpload(
                objectKey,
                presignedUrl,
                UPLOAD_METHOD,
                fileType.contentType(),
                presignedUrlExpiration
        );
    }

    private record ValidatedUploadFile(
            UploadFileCommand file,
            UploadFileType fileType
    ) {
    }
}
