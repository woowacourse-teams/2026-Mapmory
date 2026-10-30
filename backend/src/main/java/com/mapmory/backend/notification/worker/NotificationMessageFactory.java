package com.mapmory.backend.notification.worker;

import com.mapmory.backend.notification.application.port.PushMessage;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.sharevideo.ShareVideoCompletedPayload;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class NotificationMessageFactory {

    private final JsonMapper jsonMapper;

    public NotificationMessageFactory(final JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public PushMessage create(final ClaimedNotification notification, final PushToken recipient) {
        if (!"VIDEO_COMPLETED".equals(notification.eventType())) {
            throw new IllegalArgumentException("지원하지 않는 알림 유형입니다.");
        }

        ShareVideoCompletedPayload payload = jsonMapper.readValue(
                notification.payload(), ShareVideoCompletedPayload.class);
        if (payload.jobId() == null || payload.jobId() <= 0) {
            throw new IllegalArgumentException("영상 작업 ID가 올바르지 않습니다.");
        }

        return new PushMessage(
                recipient.getToken(),
                "영상이 완성됐어요",
                "여행 영상을 확인해 보세요.",
                Map.of(
                        "eventId", notification.outboxId().toString(),
                        "type", notification.eventType(),
                        "jobId", payload.jobId().toString()
                )
        );
    }
}
