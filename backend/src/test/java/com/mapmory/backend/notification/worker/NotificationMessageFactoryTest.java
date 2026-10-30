package com.mapmory.backend.notification.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.notification.application.port.PushMessage;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class NotificationMessageFactoryTest {

    private final NotificationMessageFactory factory = new NotificationMessageFactory(JsonMapper.builder().build());
    private final PushToken token = new PushToken(2L, "device-token", PushPlatform.ANDROID);

    @Test
    void 영상_완료_이벤트를_기기_발송_메시지로_변환한다() {
        ClaimedNotification event = new ClaimedNotification(7L, "claim", "VIDEO_COMPLETED", "{\"jobId\":15}", 2L, 3L);

        PushMessage message = factory.create(event, token);

        assertThat(message.token()).isEqualTo("device-token");
        assertThat(message.title()).isEqualTo("영상이 완성됐어요");
        assertThat(message.data()).containsEntry("eventId", "7")
                .containsEntry("type", "VIDEO_COMPLETED")
                .containsEntry("jobId", "15");
    }

    @Test
    void 알_수_없는_이벤트와_잘못된_영상_ID를_거절한다() {
        ClaimedNotification unknown = new ClaimedNotification(7L, "claim", "UNKNOWN", "{}", 2L, 3L);
        ClaimedNotification invalid = new ClaimedNotification(7L, "claim", "VIDEO_COMPLETED", "{\"jobId\":0}", 2L, 3L);

        assertThatThrownBy(() -> factory.create(unknown, token)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create(invalid, token)).isInstanceOf(IllegalArgumentException.class);
    }
}
