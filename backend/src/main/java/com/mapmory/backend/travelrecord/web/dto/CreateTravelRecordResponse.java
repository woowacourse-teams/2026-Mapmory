package com.mapmory.backend.travelrecord.web.dto;

import com.mapmory.backend.travelrecord.domain.TravelRecord;

public record CreateTravelRecordResponse(
        Long id
) {
    public static CreateTravelRecordResponse from(TravelRecord travelRecord) {
        return new CreateTravelRecordResponse(travelRecord.getId());
    }
}
