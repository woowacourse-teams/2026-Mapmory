package com.mapmory.backend.sharevideo.timeline;

import java.time.LocalDate;

public record TravelTimelineVisit(
        Long visitId,
        LocalDate startDate,
        LocalDate endDate
) {
}
