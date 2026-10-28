package com.mapmory.backend.upload.domain;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.upload.UploadErrorCode;

/**
 * 어떤 파일을 업로드받는가. 허용 형식, 파일 하나의 최대 크기, 요청 하나의 최대 파일 수를 정한다.
 *
 * 값은 설정(upload.policy)에서 오지만 규칙 자체는 도메인에 둔다. 설정을 읽어 만드는 일은 UploadPolicyConfig가 한다.
 */
public class UploadPolicy {

    private final long maxFileSize;
    private final int maxFilesPerRequest;

    public UploadPolicy(long maxFileSize, int maxFilesPerRequest) {
        this.maxFileSize = maxFileSize;
        this.maxFilesPerRequest = maxFilesPerRequest;
    }

    public void validateFileCount(int fileCount) {
        if (fileCount > maxFilesPerRequest) {
            throw new BusinessException(UploadErrorCode.TOO_MANY_FILES);
        }
    }

    /**
     * 형식은 Content-Type으로 정한다. 파일명은 확장자가 있을 때만 그 형식과 맞는지 본다.
     */
    public UploadFile validateFile(String fileName, String contentType, long fileSize) {
        UploadFileType fileType = UploadFileType.findByContentType(contentType)
                .orElseThrow(() -> new BusinessException(UploadErrorCode.INVALID_FILE_TYPE));
        if (!fileType.matchesFileName(fileName)) {
            throw new BusinessException(UploadErrorCode.INVALID_FILE_TYPE);
        }
        if (fileSize > maxFileSize) {
            throw new BusinessException(UploadErrorCode.FILE_SIZE_EXCEEDED);
        }
        return new UploadFile(fileType, fileSize);
    }
}
