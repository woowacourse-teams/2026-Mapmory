package com.mapmory.backend.sharevideo.timeline;

import java.util.List;

public record TravelTimelineSnapshot(
        int version,
        List<TravelTimelineStop> stops
) {

    private static final int CURRENT_VERSION = 1;

    public TravelTimelineSnapshot {
        stops = List.copyOf(stops);
    }

    public static TravelTimelineSnapshot of(final List<TravelTimelineStop> stops) {
        return new TravelTimelineSnapshot(
                CURRENT_VERSION,
                stops
        );
    }
}
