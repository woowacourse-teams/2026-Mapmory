package com.mapmory.backend.sharevideo.worker;

import com.mapmory.backend.notification.domain.NotificationOutbox;
import com.mapmory.backend.notification.domain.NotificationOutboxRepository;
import com.mapmory.backend.sharevideo.ShareVideoCompletedPayload;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ShareVideoNotificationOutboxWriter {

    private static final String EVENT_TYPE = "VIDEO_COMPLETED";

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final JsonMapper jsonMapper;

    public ShareVideoNotificationOutboxWriter(
            final NotificationOutboxRepository notificationOutboxRepository,
            final JsonMapper jsonMapper
    ) {
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.jsonMapper = jsonMapper;
    }

    // 영상 완료 처리와 같은 트랜잭션에서 Outbox를 저장
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(
            final Long jobId,
            final Long memberId,
            final Long pushTokenId,
            final LocalDateTime nowUtc
    ) {
        // 알림을 요청하지 않은 영상은 Outbox를 만들지 않음
        if (pushTokenId == null) {
            return;
        }

        String eventKey = "video:" + jobId + ":completed";
        String payload = jsonMapper.writeValueAsString(
                new ShareVideoCompletedPayload(jobId)
        );

        NotificationOutbox outbox = NotificationOutbox.pending(
                EVENT_TYPE,
                eventKey,
                payload,
                memberId,
                pushTokenId,
                nowUtc
        );

        notificationOutboxRepository.save(outbox);
    }
}
