package com.mapmory.backend.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.mapmory.backend.notification.application.port.NotificationPushSender;
import com.mapmory.backend.notification.application.port.PushMessage;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FirebaseNotificationPushSenderTest {

    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    private final FirebaseNotificationPushSender sender = new FirebaseNotificationPushSender(messaging);
    private final PushMessage message = new PushMessage("test-token", "영상 완성", "영상이 완성됐어요", Map.of("jobId", "15"));

    @Test
    void 접수_성공은_ACCEPTED로_분류한다() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("test-message-id");

        assertThat(sender.send(message))
                .isEqualTo(new NotificationPushSender.Result(NotificationPushSender.Status.ACCEPTED, null));
    }

    @Test
    void 등록_해제된_토큰을_구분한다() throws Exception {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(exception);

        assertThat(sender.send(message))
                .isEqualTo(new NotificationPushSender.Result(NotificationPushSender.Status.INVALID_TOKEN, "UNREGISTERED"));
    }

    @Test
    void 일시적인_FCM_오류를_구분한다() throws Exception {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
        when(messaging.send(any(Message.class))).thenThrow(exception);

        assertThat(sender.send(message))
                .isEqualTo(new NotificationPushSender.Result(NotificationPushSender.Status.RETRYABLE_FAILURE, "UNAVAILABLE"));
    }

    @Test
    void 구체적인_FCM_코드가_없어도_일시적인_오류를_구분한다() throws Exception {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getErrorCode()).thenReturn(ErrorCode.DEADLINE_EXCEEDED);
        when(messaging.send(any(Message.class))).thenThrow(exception);

        assertThat(sender.send(message))
                .isEqualTo(new NotificationPushSender.Result(NotificationPushSender.Status.RETRYABLE_FAILURE, "DEADLINE_EXCEEDED"));
    }

    @Test
    void 잘못된_메시지는_무효_토큰으로_단정하지_않는다() throws Exception {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        when(messaging.send(any(Message.class))).thenThrow(exception);

        assertThat(sender.send(message))
                .isEqualTo(new NotificationPushSender.Result(NotificationPushSender.Status.PERMANENT_FAILURE, "INVALID_ARGUMENT"));
    }
}
