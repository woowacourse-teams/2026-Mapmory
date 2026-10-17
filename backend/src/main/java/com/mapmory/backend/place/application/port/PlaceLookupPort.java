package com.mapmory.backend.place.application.port;

import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.model.PlaceDetails;
import java.util.List;

/** 장소 제공자에서 후보와 상세 정보를 조회하는 포트. */
public interface PlaceLookupPort {

    /** 기록에 저장할 제공자 코드. */
    String providerCode();

    /** sessionToken은 자동완성과 이어지는 상세 조회를 한 과금 세션으로 묶는다. */
    List<PlaceCandidate> search(String query, String sessionToken);

    /** 사용자가 후보를 고른 직후 지역을 추천하려고 조회한다. sessionToken이 없으면 null. */
    PlaceDetails findForSelection(String placeId, String sessionToken);

    /** 기록 저장 요청에 지역이 없을 때, 장소로 지역을 정하려고 다시 조회한다. */
    PlaceDetails findById(String placeId);

    /** 지역이 정해진 기록에 붙일 장소. 다시 조회해도 얻을 값이 없어 조회하지 않고, 국가는 null이다. */
    PlaceDetails findForRecord(String placeId);
}
