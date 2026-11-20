package com.mapmory.backend.travelrecord.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.notification.domain.NotificationOutboxRepository;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import com.mapmory.backend.travelrecord.exception.TravelRecordErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PhotoRecordNotificationOutboxWriter {
    private final NotificationOutboxRepository outboxRepository;
    private final PushTokenRepository pushTokenRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public PhotoRecordNotificationOutboxWriter(NotificationOutboxRepository outboxRepository,
            PushTokenRepository pushTokenRepository, JsonMapper jsonMapper, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.pushTokenRepository = pushTokenRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Long recordId, Long memberId, Long pushTokenId) {
        if (pushTokenId == null) {
            return;
        }
        if (pushTokenRepository.findByIdAndMemberId(pushTokenId, memberId).isEmpty()) {
            throw new BusinessException(TravelRecordErrorCode.TRAVEL_RECORD_PUSH_TARGET_NOT_FOUND);
        }
        outboxRepository.enqueueOnce("PHOTO_RECORD_COMPLETED", "photo-record:" + recordId + ":completed",
                jsonMapper.writeValueAsString(new PhotoRecordCompletedPayload(recordId)),
                memberId, pushTokenId, LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
    }
}
