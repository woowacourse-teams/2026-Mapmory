package com.mapmory.backend.notification.worker;

public record ClaimedNotification(
        Long outboxId,
        String claimToken,
        String eventType,
        String payload,
        Long memberId,
        Long pushTokenId
) {
}
