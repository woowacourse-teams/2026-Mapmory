package com.mapmory.backend.place.application;

import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.application.port.PlaceRateLimitPort;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlaceSearchService {

    private final PlaceLookupPort placeLookupPort;
    private final PlaceRateLimitPort rateLimitPort;

    public PlaceSearchService(PlaceLookupPort placeLookupPort, PlaceRateLimitPort rateLimitPort) {
        this.placeLookupPort = placeLookupPort;
        this.rateLimitPort = rateLimitPort;
    }

    public List<PlaceCandidate> search(Long memberId, String query) {
        rateLimitPort.checkSearch(memberId);
        return placeLookupPort.search(query.strip());
    }
}
