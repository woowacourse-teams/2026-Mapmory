package com.mapmory.backend.place.application;

import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.region.application.RegionSuggester;
import org.springframework.stereotype.Service;

@Service
public class PlaceSelectionService {

    private final PlaceLookupPort placeLookupPort;
    private final RegionSuggester regionSuggester;

    public PlaceSelectionService(PlaceLookupPort placeLookupPort, RegionSuggester regionSuggester) {
        this.placeLookupPort = placeLookupPort;
        this.regionSuggester = regionSuggester;
    }

    public SelectedPlace select(String placeId, String sessionToken) {
        PlaceDetails place = placeLookupPort.findDetails(PlaceId.from(placeId), sessionToken);
        return new SelectedPlace(place,
                regionSuggester.suggest(place.address()).orElse(null));
    }
}
