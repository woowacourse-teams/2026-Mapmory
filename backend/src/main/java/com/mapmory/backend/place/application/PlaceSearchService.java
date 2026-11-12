package com.mapmory.backend.place.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.domain.PlaceCandidate;
import com.mapmory.backend.place.domain.SearchQuery;
import com.mapmory.backend.place.lookuplog.PlaceLookupContext;
import com.mapmory.backend.place.lookuplog.PlaceLookupRecorder;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlaceSearchService {

    private final PlaceLookupPort placeLookupPort;
    private final PlaceLookupRecorder recorder;

    public PlaceSearchService(PlaceLookupPort placeLookupPort, PlaceLookupRecorder recorder) {
        this.placeLookupPort = placeLookupPort;
        this.recorder = recorder;
    }

    /** 검색어와 후보 수를 place_lookup_log에 남긴다. 실패도 오류 코드와 함께 남기고 그대로 던진다. */
    public List<PlaceCandidate> search(PlaceLookupContext context, String query, String sessionToken) {
        List<PlaceCandidate> candidates;
        try {
            candidates = placeLookupPort.search(SearchQuery.from(query), sessionToken);
        } catch (BusinessException exception) {
            recorder.searchFailed(context, sessionToken, query, exception.getErrorCode().code());
            throw exception;
        }
        recorder.searched(context, sessionToken, query, candidates.size());
        return candidates;
    }
}
