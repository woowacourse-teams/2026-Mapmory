package com.mapmory.backend.upload.service.port;

/**
 * 업로드 저장소에 객체가 존재하는지 확인하는 포트.
 *
 * 저장소 확인 자체가 실패한 경우에는 객체가 없는 경우와 구분해 예외를 던진다.
 */
public interface UploadedObjectChecker {

    boolean exists(String objectKey);
}
