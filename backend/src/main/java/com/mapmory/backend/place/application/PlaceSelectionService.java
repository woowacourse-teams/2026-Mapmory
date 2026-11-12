package com.mapmory.backend.place.application;

import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.dropoff.DropOffContext;
import com.mapmory.backend.dropoff.DropOffRecorder;
import com.mapmory.backend.region.application.RegionSuggester;
import org.springframework.stereotype.Service;

@Service
public class PlaceSelectionService {

    private final PlaceLookupPort placeLookupPort;
    private final RegionSuggester regionSuggester;
    private final DropOffRecorder recorder;

    public PlaceSelectionService(PlaceLookupPort placeLookupPort, RegionSuggester regionSuggester,
                                 DropOffRecorder recorder) {
        this.placeLookupPort = placeLookupPort;
        this.regionSuggester = regionSuggester;
        this.recorder = recorder;
    }

    /** 고른 장소와 찾은 지역을 drop_off_log에 남긴다. 실패도 오류 코드와 함께 남기고 그대로 던진다. */
    public SelectedPlace select(DropOffContext context, String placeId, String sessionToken) {
        SelectedPlace selected;
        try {
            PlaceDetails place = placeLookupPort.findDetails(PlaceId.from(placeId), sessionToken);
            selected = new SelectedPlace(place, regionSuggester.suggest(place.address()).orElse(null));
        } catch (RuntimeException exception) {
            recorder.selectFailed(context, sessionToken, placeId, DropOffRecorder.errorCodeOf(exception));
            throw exception;
        }
        recorder.selected(context, sessionToken, placeId, selected.countryCode(),
                selected.suggestedRegion() == null ? null : selected.suggestedRegion().getRegionCode());
        return selected;
    }
}
