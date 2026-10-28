package com.mapmory.backend.upload.service.port;

import com.mapmory.backend.upload.domain.UploadFileType;

/**
 * 업로드할 파일이 저장소에 놓일 키를 정한다.
 *
 * 키는 회원별로 묶이고 다른 사용자가 추측할 수 없어야 한다. 경로와 접두사는 구현이 정한다.
 */
public interface ObjectKeyGenerator {

    String generate(Long memberId, UploadFileType fileType);

    /**
     * 이 회원에게 발급한 형식의 키인가. 다른 회원이나 다른 용도의 경로면 false다.
     */
    boolean isIssuedTo(Long memberId, String objectKey);
}
