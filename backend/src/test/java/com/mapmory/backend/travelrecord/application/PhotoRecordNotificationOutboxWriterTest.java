package com.mapmory.backend.travelrecord.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.notification.domain.NotificationOutboxRepository;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PhotoRecordNotificationOutboxWriterTest {
    private final NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
    private final PushTokenRepository tokens = mock(PushTokenRepository.class);
    private final PhotoRecordNotificationOutboxWriter writer = new PhotoRecordNotificationOutboxWriter(
            outbox, tokens, JsonMapper.builder().build(),
            Clock.fixed(Instant.parse("2026-11-11T00:00:00Z"), ZoneOffset.ofHours(9)));

    @Test
    void 알림을_생략하면_수신_등록도_조회하지_않는다() {
        writer.save(42L, 2L, null);
        verifyNoInteractions(outbox, tokens);
    }

    @Test
    void 현재_회원의_등록만_허용한다() {
        when(tokens.findByIdAndMemberId(3L, 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> writer.save(42L, 2L, 3L)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().code())
                .isEqualTo("TRAVEL_RECORD_PUSH_TARGET_NOT_FOUND");
        verifyNoInteractions(outbox);
    }

    @Test
    void 사건_키와_기록_ID를_UTC_시각으로_저장한다() {
        when(tokens.findByIdAndMemberId(3L, 2L))
                .thenReturn(Optional.of(new PushToken(2L, "device", PushPlatform.IOS)));
        writer.save(42L, 2L, 3L);
        verify(outbox).enqueueOnce(eq("PHOTO_RECORD_COMPLETED"), eq("photo-record:42:completed"),
                eq("{\"recordId\":42}"), eq(2L), eq(3L), eq(LocalDateTime.of(2026, 11, 11, 0, 0)));
    }
}
