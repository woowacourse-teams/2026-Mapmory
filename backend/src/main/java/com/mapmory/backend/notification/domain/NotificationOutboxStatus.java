package com.mapmory.backend.notification.domain;

public enum NotificationOutboxStatus {

    // 아직 발송 워커가 처리하지 않은 이벤트
    PENDING,

    // 발송 워커가 선점해 처리 중인 이벤트
    PROCESSING,

    // FCM이 발송 요청을 접수한 이벤트
    SENT,

    // 재시도 한도 초과 또는 복구할 수 없는 오류로 실패한 이벤트
    FAILED,

    // 푸시 등록이 삭제되는 등 발송 대상이 없어 생략한 이벤트
    SKIPPED
}
