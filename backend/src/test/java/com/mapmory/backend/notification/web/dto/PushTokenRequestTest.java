package com.mapmory.backend.notification.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.notification.domain.PushPlatform;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PushTokenRequestTest {
    @Test
    void 기존_JSON은_설정_기본값을_유지한다() {
        PushTokenRequest request = JsonMapper.builder().build().readValue(
                "{\"token\":\"token\",\"platform\":\"ANDROID\"}", PushTokenRequest.class);
        assertThat(request.previousPushTokenId()).isNull();
        assertThat(request.photoRecordCompletionEnabled()).isTrue();
        assertThat(request.travelVideoCompletionEnabled()).isTrue();
    }

    @Test
    void 이전_등록_ID는_양수여야_한다() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(
                    new PushTokenRequest("token", PushPlatform.ANDROID, 0L, false, false)))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("previousPushTokenId");
        }
    }
}
