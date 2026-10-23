package com.mapmory.backend.sharevideo.timeline;

import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.travelrecord.TravelRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TravelTimelineSnapshotFactory {

    private static final String KOREA_COUNTRY_CODE = "KR";

    public TravelTimelineSnapshot create(final List<TravelRecord> travelRecords) {
        final List<TravelTimelineStop> stops = new ArrayList<>();

        for (final TravelRecord travelRecord : travelRecords) {
            final Region country = findCountry(travelRecord.getRegion());

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

    private Region findCountry(final Region region) {
        if (region.getRegionType() == RegionType.COUNTRY) {
            return region;
        }
        return region.getRoot();
    }

    private boolean canMergeWithPreviousKorea(final List<TravelTimelineStop> stops, final Region country) {
        if (stops.isEmpty()) {
            return false;
        }

        if (!KOREA_COUNTRY_CODE.equals(country.getRegionCode())) {
            return false;
        }

        final TravelTimelineStop previousStop = stops.get(stops.size() - 1);

        return KOREA_COUNTRY_CODE.equals(previousStop.countryCode());
    }

    private void appendToPreviousStop(final List<TravelTimelineStop> stops, final TravelTimelineVisit visit) {
        final int lastIndex = stops.size() - 1;
        final TravelTimelineStop previousStop = stops.get(lastIndex);

        stops.set(lastIndex, previousStop.append(visit));
    }
}
