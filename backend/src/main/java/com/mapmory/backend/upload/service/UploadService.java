package com.mapmory.backend.upload.service;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.application.ActiveMemberGuard;
import com.mapmory.backend.upload.PresignedUpload;
import com.mapmory.backend.upload.domain.UploadFile;
import com.mapmory.backend.upload.domain.UploadPolicy;
import com.mapmory.backend.upload.ratelimit.UploadRateLimiter;
import com.mapmory.backend.upload.service.port.ObjectKeyGenerator;
import com.mapmory.backend.upload.service.port.PresignedRequest;
import com.mapmory.backend.upload.service.port.PresignedUrlProvider;
import com.mapmory.backend.dropoff.DropOffContext;
import com.mapmory.backend.dropoff.DropOffRecorder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UploadService {

    private final UploadPolicy uploadPolicy;
    private final ObjectKeyGenerator objectKeyGenerator;
    private final PresignedUrlProvider presignedUrlProvider;
    private final ActiveMemberGuard activeMemberGuard;
    private final UploadRateLimiter uploadRateLimiter;
    private final DropOffRecorder dropOffRecorder;

    public UploadService(
            UploadPolicy uploadPolicy,
            ObjectKeyGenerator objectKeyGenerator,
            PresignedUrlProvider presignedUrlProvider,
            ActiveMemberGuard activeMemberGuard,
            UploadRateLimiter uploadRateLimiter,
            DropOffRecorder dropOffRecorder
    ) {
        this.uploadPolicy = uploadPolicy;
        this.objectKeyGenerator = objectKeyGenerator;
        this.presignedUrlProvider = presignedUrlProvider;
        this.activeMemberGuard = activeMemberGuard;
        this.uploadRateLimiter = uploadRateLimiter;
        this.dropOffRecorder = dropOffRecorder;
    }

    /**
     * 탈퇴 뒤 남은 access token으로 S3에 올리지 못하게 회원을 먼저 확인한다. 이 확인에 커넥션을 한 번 빌린다.
     * 한도는 그보다 앞에서 본다. 한도를 넘긴 요청이 DB 커넥션을 쓰지 않게 하려는 것이다.
     *
     * <p>거절되면 사용자가 기록을 저장하지 못하므로 막힌 지점 기록(drop_off_log)에 남기고 그대로 던진다.
     * 파일 하나가 걸렸으면 그 파일을, 아니면(장 수·한도 등) 가장 큰 파일의 크기·형식을 남긴다.
     */
    public List<PresignedUpload> createPresignedUrls(DropOffContext context, Member member,
                                                     List<UploadFileCommand> files) {
        UploadFileCommand current = largest(files);
        try {
            uploadPolicy.validateFileCount(files.size());
            List<UploadFile> validatedFiles = new ArrayList<>();
            for (UploadFileCommand file : files) {
                current = file;
                validatedFiles.add(uploadPolicy.validateFile(file.fileName(), file.contentType(), file.fileSize()));
            }
            current = largest(files);
            uploadRateLimiter.check(member.getId(), validatedFiles.size());
            activeMemberGuard.require(member.getId());

            return validatedFiles.stream()
                    .map(file -> createPresignedUpload(member.getId(), file))
                    .toList();
        } catch (RuntimeException exception) {
            dropOffRecorder.uploadRejected(context, files.size(),
                    current == null ? null : current.fileSize(),
                    current == null ? null : current.contentType(),
                    DropOffRecorder.errorCodeOf(exception));
            throw exception;
        }
    }

    private static UploadFileCommand largest(List<UploadFileCommand> files) {
        return files.stream().max(Comparator.comparingLong(UploadFileCommand::fileSize)).orElse(null);
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
