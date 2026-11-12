package com.mapmory.backend.place.application;

import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.domain.PlaceCandidate;
import com.mapmory.backend.place.domain.SearchQuery;
import com.mapmory.backend.dropoff.DropOffContext;
import com.mapmory.backend.dropoff.DropOffRecorder;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlaceSearchService {

    private final PlaceLookupPort placeLookupPort;
    private final DropOffRecorder recorder;

    public PlaceSearchService(PlaceLookupPort placeLookupPort, DropOffRecorder recorder) {
        this.placeLookupPort = placeLookupPort;
        this.recorder = recorder;
    }

    /** 검색어와 후보 수를 drop_off_log에 남긴다. 실패도 오류 코드와 함께 남기고 그대로 던진다. */
    public List<PlaceCandidate> search(DropOffContext context, String query, String sessionToken) {
        List<PlaceCandidate> candidates;
        try {
            candidates = placeLookupPort.search(SearchQuery.from(query), sessionToken);
        } catch (RuntimeException exception) {
            recorder.searchFailed(context, sessionToken, query, DropOffRecorder.errorCodeOf(exception));
            throw exception;
        }
        recorder.searched(context, sessionToken, query, candidates.size());
        return candidates;
    }
}
