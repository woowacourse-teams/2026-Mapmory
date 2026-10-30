package com.mapmory.backend.notification.infrastructure;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.mapmory.backend.notification.application.port.NotificationPushSender;
import com.mapmory.backend.notification.application.port.PushMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "notification.fcm",
        name = "enabled",
        havingValue = "true"
)
public class FirebaseNotificationPushSender implements NotificationPushSender {

    private final FirebaseMessaging firebaseMessaging;

    public FirebaseNotificationPushSender(final FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public Result send(final PushMessage pushMessage) {
        try {
            Message message = Message.builder()
                    .setToken(pushMessage.token())
                    .setNotification(
                            Notification.builder()
                                    .setTitle(pushMessage.title())
                                    .setBody(pushMessage.body())
                                    .build()
                    )
                    .putAllData(pushMessage.data())
                    .build();

            firebaseMessaging.send(message);

            return new Result(Status.ACCEPTED, null);
        } catch (FirebaseMessagingException exception) {
            return classify(exception);
        } catch (IllegalArgumentException exception) {
            return new Result(Status.PERMANENT_FAILURE, "INVALID_MESSAGE");
        }
    }

    private Result classify(final FirebaseMessagingException exception) {
        MessagingErrorCode messagingCode = exception.getMessagingErrorCode();

        if (messagingCode != null) {
            return switch (messagingCode) {
                case UNREGISTERED ->
                        new Result(Status.INVALID_TOKEN, messagingCode.name());

                case UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED ->
                        new Result(Status.RETRYABLE_FAILURE, messagingCode.name());

                default ->
                        new Result(Status.PERMANENT_FAILURE, messagingCode.name());
            };
        }

        ErrorCode errorCode = exception.getErrorCode();

        if (errorCode == null) {
            return new Result(Status.PERMANENT_FAILURE, "UNKNOWN");
        }

        return switch (errorCode) {
            case UNAVAILABLE, INTERNAL, DEADLINE_EXCEEDED, RESOURCE_EXHAUSTED ->
                    new Result(Status.RETRYABLE_FAILURE, errorCode.name());

            default ->
                    new Result(Status.PERMANENT_FAILURE, errorCode.name());
        };
    }
}