package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.travelrecord.dto.CreateTravelRecordResponse;
import com.mapmory.backend.travelrecord.dto.TravelRecordRequest;
import com.mapmory.backend.travelrecord.dto.TravelRecordResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TravelRecordControllerTest {

    @Mock
    private TravelRecordService travelRecordService;

    @InjectMocks
    private TravelRecordController travelRecordController;

    @Test
    void createsTravelRecord() {
        TravelRecordRequest request = new TravelRecordRequest(
                "JP",
                null,
                null,
                "일본 여행",
                "",
                LocalDate.of(2026, 8, 11),
                null,
                List.of()
        );
        TravelRecord travelRecord = TravelRecord.of(
                null,
                null,
                request.title(),
                request.content(),
                request.startDate(),
                request.endDate()
        );
        ReflectionTestUtils.setField(travelRecord, "id", 1L);

        when(travelRecordService.create(10L, request)).thenReturn(travelRecord);

        ResponseEntity<TravelRecordResponse<CreateTravelRecordResponse>> response =
                travelRecordController.create(10L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(
                TravelRecordResponse.of(new CreateTravelRecordResponse(1L))
        );
        verify(travelRecordService).create(10L, request);
    }
}
