package com.mapmory.backend.place.application;

import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlaceSearchService {

    private final PlaceLookupPort placeLookupPort;

    public PlaceSearchService(PlaceLookupPort placeLookupPort) {
        this.placeLookupPort = placeLookupPort;
    }

    public List<PlaceCandidate> search(String query, String sessionToken) {
        return placeLookupPort.search(query.strip(), sessionToken);
    }
}
