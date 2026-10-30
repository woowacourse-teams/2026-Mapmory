package com.mapmory.backend.notification.application.port;

public interface NotificationPushSender {

    Result send(PushMessage message);

    enum Status {
        ACCEPTED,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE,
        INVALID_TOKEN
    }

    record Result(
            Status status,
            String errorCode
    ) {
    }
}
