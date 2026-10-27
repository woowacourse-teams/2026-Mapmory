package com.mapmory.backend.place.application.port;

import com.mapmory.backend.place.domain.PlaceCandidate;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.place.domain.PlaceProvider;
import java.util.List;

/** 장소 제공자에서 후보와 상세 정보를 조회하는 포트. */
public interface PlaceLookupPort {

    /** 이 포트의 제공자. 제공자를 쓸 수 없으면 PLACE_PROVIDER_UNAVAILABLE이다. */
    PlaceProvider provider();

    /** sessionToken은 자동완성과 이어지는 상세 조회를 한 과금 세션으로 묶는다. */
    List<PlaceCandidate> search(String query, String sessionToken);

    /** 후보를 고른 직후면 검색 때의 sessionToken으로 세션을 닫는다. 그 밖의 조회는 null이다. */
    PlaceDetails findDetails(PlaceId id, String sessionToken);
}
