package com.mapmory.backend.upload.storage;

/**
 * 일정 시간만 열리는 업로드·조회 URL을 서명한다. 유효시간은 구현이 정하고 결과에 담는다.
 */
public interface PresignedUrlProvider {

    /**
     * 업로드용 URL. Content-Type과 크기를 서명에 넣어 앱이 다른 값으로 올리지 못하게 한다.
     */
    PresignedRequest createPresignedPutUrl(
            String objectKey,
            String contentType,
            long contentLength
    );

    PresignedRequest createPresignedGetUrl(String objectKey);
}
