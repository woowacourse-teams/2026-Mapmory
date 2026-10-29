package com.mapmory.backend.sharevideo;

/**
 * 영상 생성 완료 알림을 처리할 때 필요한 데이터
 */
public record ShareVideoCompletedPayload(Long jobId) {
}
