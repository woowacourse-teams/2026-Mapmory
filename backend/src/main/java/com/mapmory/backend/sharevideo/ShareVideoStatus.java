package com.mapmory.backend.sharevideo;

public enum ShareVideoStatus {
    // 작업 대기
    PENDING,
    // 영상 생성 중
    RENDERING,
    // 생성 완료
    COMPLETED,
    // 생성 실패
    FAILED
}
