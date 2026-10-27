package com.mapmory.backend.sharevideo.timeline;

import com.mapmory.backend.region.Region;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

            if (canMergeWithPreviousCountry(stops, country)) {
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

    private boolean canMergeWithPreviousCountry(final List<TravelTimelineStop> stops, final Region country) {
        if (stops.isEmpty()) {
            return false;
        }

        final TravelTimelineStop previousStop = stops.get(stops.size() - 1);

        return Objects.equals(country.getRegionCode(), previousStop.countryCode());
    }

    private void appendToPreviousStop(final List<TravelTimelineStop> stops, final TravelTimelineVisit visit) {
        final int lastIndex = stops.size() - 1;
        final TravelTimelineStop previousStop = stops.get(lastIndex);

        stops.set(lastIndex, previousStop.append(visit));
    }
}
