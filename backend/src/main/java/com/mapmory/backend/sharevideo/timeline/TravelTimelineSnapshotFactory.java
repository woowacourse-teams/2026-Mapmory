package com.mapmory.backend.sharevideo.timeline;

import com.mapmory.backend.region.Region;
import com.mapmory.backend.travelrecord.application.model.TravelRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TravelTimelineSnapshotFactory {

    public TravelTimelineSnapshot create(final List<TravelRecord> travelRecords) {
        final List<TravelTimelineStop> stops = new ArrayList<>();

        for (final TravelRecord travelRecord : travelRecords) {
            final Region country = travelRecord.getRegion().country();

            final TravelTimelineVisit visit = new TravelTimelineVisit(
                    travelRecord.getId(),
                    travelRecord.getStartDate(),
                    travelRecord.getEndDate()
            );

            if (canMergeWithPreviousKorea(stops, country)) {
                appendToPreviousStop(stops, visit);
                continue;
            }

            stops.add(
                    TravelTimelineStop.single(
                            country.getRegionCode(),
                            country.getName(),
                            visit
                    )
            );
        }

        return TravelTimelineSnapshot.of(stops);
    }

    private boolean canMergeWithPreviousKorea(final List<TravelTimelineStop> stops, final Region country) {
        if (stops.isEmpty()) {
            return false;
        }

        if (!Region.KOREA.equals(country.getRegionCode())) {
            return false;
        }

        final TravelTimelineStop previousStop = stops.get(stops.size() - 1);

        return Region.KOREA.equals(previousStop.countryCode());
    }

    private void appendToPreviousStop(final List<TravelTimelineStop> stops, final TravelTimelineVisit visit) {
        final int lastIndex = stops.size() - 1;
        final TravelTimelineStop previousStop = stops.get(lastIndex);

        stops.set(lastIndex, previousStop.append(visit));
    }
}
