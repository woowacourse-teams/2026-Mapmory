package com.mapmory.backend.upload.storage;

/**
 * 업로드된 객체가 저장소에 실제로 있는지 확인한다.
 *
 * 앱이 presigned URL로 S3에 직접 올리므로 서버는 업로드 성공 여부를 모른다. (ADR 0016)
 * PresignedUrlProvider와 마찬가지로 S3를 이 인터페이스 뒤에 가둬, 상위 계층이 스토리지
 * 구현을 모르게 하고 인수 테스트가 S3 없이 돌 수 있게 한다.
 */
public interface UploadedObjectChecker {

    /**
     * 객체가 있으면 true, 없으면 false를 반환한다.
     *
     * 저장소 확인 자체가 실패한 경우(장애·권한 등)는 "없음"과 구분해 예외로 알린다.
     * 둘을 뭉뚱그리면 장애 중에 "다시 업로드하라"고 안내하게 되고, 다시 올려도 같은 장애에 걸린다.
     */
    boolean exists(String objectKey);
}
