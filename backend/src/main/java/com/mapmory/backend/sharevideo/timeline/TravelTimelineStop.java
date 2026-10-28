package com.mapmory.backend.sharevideo.timeline;

import java.util.ArrayList;
import java.util.List;

public record TravelTimelineStop(
        String countryCode,
        String countryName,
        List<TravelTimelineVisit> visits
) {

    public TravelTimelineStop {
        visits = List.copyOf(visits);
    }

    public static TravelTimelineStop single(
            final String countryCode,
            final String countryName,
            final TravelTimelineVisit visit
    ) {
        return new TravelTimelineStop(
                countryCode,
                countryName,
                List.of(visit)
        );
    }

    public TravelTimelineStop append(final TravelTimelineVisit visit) {
        final List<TravelTimelineVisit> appendedVisits = new ArrayList<>(visits);

        appendedVisits.add(visit);

        return new TravelTimelineStop(
                countryCode,
                countryName,
                appendedVisits
        );
    }
}
