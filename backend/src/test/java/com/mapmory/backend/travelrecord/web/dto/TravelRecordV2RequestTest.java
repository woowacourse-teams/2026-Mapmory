package com.mapmory.backend.travelrecord.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TravelRecordV2RequestTest {
    @Test
    void 촬영일과_수신_등록_ID를_함께_서비스에_전달한다() {
        var photo = new TravelRecordV2Request.PhotoRequest("photo-key",
                JsonMapper.builder().build().readTree("\"2026-11-10\""));
        var request = new TravelRecordV2Request("KR", "27", "27110", "title", "content",
                LocalDate.of(2026, 11, 10), null, List.of(photo), List.of(), null, 7L);
        var command = request.toCommand();
        assertThat(command.pushTokenId()).isEqualTo(7L);
        assertThat(command.objectKeys()).containsExactly("photo-key");
        assertThat(command.capturedDates()).containsEntry("photo-key", "2026-11-10");
    }

    @Test
    void 수신_등록_ID는_선택인_양수다() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new TravelRecordV2Request("KR", null, null, null, null,
                    LocalDate.of(2026, 11, 10), null, List.of(), List.of(), null, 0L);
            assertThat(factory.getValidator().validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString()).contains("pushTokenId");
        }
    }
}
