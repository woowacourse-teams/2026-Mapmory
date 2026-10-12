package com.mapmory.backend.place.application.port;

import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.model.PlaceDetails;
import java.util.List;

/** 장소 제공자에서 후보와 상세 정보를 조회하는 포트. */
public interface PlaceLookupPort {

    /** 기록에 저장할 제공자 코드. 제공자마다 장소 ID 형식이 달라 ID로 판단한다. */
    String providerCode(String placeId);

    /** sessionToken은 자동완성과 이어지는 상세 조회를 한 과금 세션으로 묶는다. 없으면 null. */
    List<PlaceCandidate> search(String query, String sessionToken);

    /** 사용자가 후보를 고른 직후 화면에 보여 줄 이름까지 조회한다. */
    PlaceDetails findForSelection(String placeId, String sessionToken);

    /**
     * 기록 저장 시 국가를 검증하려고 다시 조회한다. 제공자 약관상 저장할 수 없는 이름은 null로 돌려준다.
     */
    PlaceDetails findById(String placeId);
}
