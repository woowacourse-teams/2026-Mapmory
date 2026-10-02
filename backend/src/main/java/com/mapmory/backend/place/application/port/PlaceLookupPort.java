package com.mapmory.backend.place.application.port;

import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.model.PlaceDetails;
import java.util.List;

/** 장소 제공자에서 후보와 상세 정보를 조회하는 포트. */
public interface PlaceLookupPort {

    String providerCode();

    List<PlaceCandidate> search(String query);

    PlaceDetails findById(String placeId);
}
