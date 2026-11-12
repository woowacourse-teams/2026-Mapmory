package com.mapmory.backend.place.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.place.lookuplog.PlaceLookupContext;
import com.mapmory.backend.place.lookuplog.PlaceLookupRecorder;
import com.mapmory.backend.region.application.RegionSuggester;
import org.springframework.stereotype.Service;

@Service
public class PlaceSelectionService {

    private final PlaceLookupPort placeLookupPort;
    private final RegionSuggester regionSuggester;
    private final PlaceLookupRecorder recorder;

    public PlaceSelectionService(PlaceLookupPort placeLookupPort, RegionSuggester regionSuggester,
                                 PlaceLookupRecorder recorder) {
        this.placeLookupPort = placeLookupPort;
        this.regionSuggester = regionSuggester;
        this.recorder = recorder;
    }

    /** 고른 장소와 찾은 지역을 place_lookup_log에 남긴다. 실패도 오류 코드와 함께 남기고 그대로 던진다. */
    public SelectedPlace select(PlaceLookupContext context, String placeId, String sessionToken) {
        SelectedPlace selected;
        try {
            PlaceDetails place = placeLookupPort.findDetails(PlaceId.from(placeId), sessionToken);
            selected = new SelectedPlace(place, regionSuggester.suggest(place.address()).orElse(null));
        } catch (BusinessException exception) {
            recorder.selectFailed(context, sessionToken, placeId, exception.getErrorCode().code());
            throw exception;
        }
        recorder.selected(context, sessionToken, placeId, selected.countryCode(),
                selected.suggestedRegion() == null ? null : selected.suggestedRegion().getRegionCode());
        return selected;
    }
}
