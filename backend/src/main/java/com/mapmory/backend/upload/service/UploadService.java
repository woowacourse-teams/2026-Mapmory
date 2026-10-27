package com.mapmory.backend.upload.service;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.application.ActiveMemberGuard;
import com.mapmory.backend.upload.PresignedUpload;
import com.mapmory.backend.upload.domain.UploadFile;
import com.mapmory.backend.upload.domain.UploadPolicy;
import com.mapmory.backend.upload.service.port.ObjectKeyGenerator;
import com.mapmory.backend.upload.service.port.PresignedRequest;
import com.mapmory.backend.upload.service.port.PresignedUrlProvider;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UploadService {

    private final UploadPolicy uploadPolicy;
    private final ObjectKeyGenerator objectKeyGenerator;
    private final PresignedUrlProvider presignedUrlProvider;
    private final ActiveMemberGuard activeMemberGuard;

    public UploadService(
            UploadPolicy uploadPolicy,
            ObjectKeyGenerator objectKeyGenerator,
            PresignedUrlProvider presignedUrlProvider,
            ActiveMemberGuard activeMemberGuard
    ) {
        this.uploadPolicy = uploadPolicy;
        this.objectKeyGenerator = objectKeyGenerator;
        this.presignedUrlProvider = presignedUrlProvider;
        this.activeMemberGuard = activeMemberGuard;
    }

    /** 탈퇴 뒤 남은 access token으로 S3에 올리지 못하게 회원을 먼저 확인한다. 이 확인에 커넥션을 한 번 빌린다. */
    public List<PresignedUpload> createPresignedUrls(Member member, List<UploadFileCommand> files) {
        uploadPolicy.validateFileCount(files.size());
        List<UploadFile> validatedFiles = files.stream()
                .map(file -> uploadPolicy.validateFile(file.fileName(), file.contentType(), file.fileSize()))
                .toList();
        activeMemberGuard.require(member.getId());

        return validatedFiles.stream()
                .map(file -> createPresignedUpload(member.getId(), file))
                .toList();
    }

    private PresignedUpload createPresignedUpload(Long memberId, UploadFile file) {
        String objectKey = objectKeyGenerator.generate(memberId, file.fileType());
        PresignedRequest presigned = presignedUrlProvider.createPresignedPutUrl(
                objectKey,
                file.contentType(),
                file.size()
        );
        return new PresignedUpload(
                objectKey,
                presigned.url(),
                presigned.method(),
                file.contentType(),
                presigned.expiration()
        );
    }
}
