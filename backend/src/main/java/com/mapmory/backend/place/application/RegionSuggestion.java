package com.mapmory.backend.place.application;

import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionAddress;

/**
 * 제공자가 준 주소와 서버가 추천한 지역. 장소 선택과 사진 좌표 판정이 같은 규칙으로 국가와 직접 선택 여부를 정하도록
 * 한곳에 둔다. 지역을 찾지 못하면 suggestedRegion은 null이다.
 */
record RegionSuggestion(RegionAddress address, Region suggestedRegion) {

    /** 추천 지역이 없으면 사용자가 지역을 직접 고른다. */
    boolean manualRegionRequired() {
        return suggestedRegion == null;
    }

    /**
     * 제공자가 국가를 주지 않아도(Google의 독도) 지역을 찾았으면 그 지역의 국가다. 둘 다 없으면 null이다.
     * 앱은 이 코드로 지역 목록을 찾아서, 비어 있으면 지역을 직접 고르게 한다.
     */
    String countryCode() {
        String providerCountryCode = address.countryCode();
        if (providerCountryCode != null || suggestedRegion == null) {
            return providerCountryCode;
        }
        return suggestedRegion.country().getRegionCode();
    }
}
