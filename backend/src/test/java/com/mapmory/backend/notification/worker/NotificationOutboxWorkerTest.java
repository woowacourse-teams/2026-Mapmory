package com.mapmory.backend.notification.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.notification.application.port.NotificationPushSender;
import com.mapmory.backend.notification.application.port.PushMessage;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationOutboxWorkerTest {

    private final NotificationOutboxClaimer claimer = mock(NotificationOutboxClaimer.class);
    private final NotificationRecipientFinder finder = mock(NotificationRecipientFinder.class);
    private final NotificationMessageFactory factory = mock(NotificationMessageFactory.class);
    private final NotificationPushSender sender = mock(NotificationPushSender.class);
    private final NotificationOutboxResultService results = mock(NotificationOutboxResultService.class);
    private final NotificationOutboxWorker worker = new NotificationOutboxWorker(claimer, finder, factory, sender, results);
    private final ClaimedNotification event = new ClaimedNotification(7L, "claim", "VIDEO_COMPLETED", "{}", 2L, 3L);
    private final PushToken token = new PushToken(2L, "test-token", PushPlatform.ANDROID);
    private final PushMessage message = new PushMessage("test-token", "제목", "본문", Map.of());

    @Test
    void 수신_등록이_없으면_발송하지_않고_생략한다() {
        when(claimer.claimNextForType("VIDEO_COMPLETED")).thenReturn(Optional.of(event));
        when(finder.find(event)).thenReturn(Optional.empty());

        worker.processNext();

        verify(results).markSkipped(7L, "claim", "RECIPIENT_NOT_FOUND");
        verify(sender, never()).send(any());
    }

    @Test
    void FCM_접수_성공을_저장한다() {
        prepareMessage();
        when(sender.send(message)).thenReturn(new NotificationPushSender.Result(NotificationPushSender.Status.ACCEPTED, null));

        worker.processNext();

        verify(results).markSent(7L, "claim");
    }

    @Test
    void 발송_오류는_재시도하지_않고_실패로_기록한다() {
        prepareMessage();
        when(sender.send(message)).thenReturn(new NotificationPushSender.Result(NotificationPushSender.Status.RETRYABLE_FAILURE, "UNAVAILABLE"));

        worker.processNext();

        verify(results).markFailed(7L, "claim", "UNAVAILABLE");
        verify(results, never()).scheduleRetry(any(), any(), any(), any());
    }

    @Test
    void 잘못된_이벤트는_FCM을_호출하지_않는다() {
        when(claimer.claimNextForType("VIDEO_COMPLETED")).thenReturn(Optional.of(event));
        when(finder.find(event)).thenReturn(Optional.of(token));
        when(factory.create(event, token)).thenThrow(new IllegalArgumentException("invalid"));

        worker.processNext();

        verify(results).markFailed(7L, "claim", "INVALID_EVENT");
        verify(sender, never()).send(any());
    }

    private void prepareMessage() {
        when(claimer.claimNextForType("VIDEO_COMPLETED")).thenReturn(Optional.of(event));
        when(finder.find(event)).thenReturn(Optional.of(token));
        when(factory.create(event, token)).thenReturn(message);
    }
}
