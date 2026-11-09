package com.mapmory.backend.place.infrastructure.google.geocoding;

import com.mapmory.backend.place.infrastructure.google.GoogleAddressMapper;
import com.mapmory.backend.region.domain.RegionAddress;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

class GoogleGeocodingMapper {

    /**
     * Geocoding v4 역지오코딩 응답에서 주소를 꺼낸다. 주소 구성요소는 Places API (New)와 같은 모양이다.
     * 결과는 좁은 것(번지)부터 오고 플러스 코드처럼 일부 단위만 있는 결과도 섞여 있어, {@link #geocodeRank} 순서로
     * 가장 앞선 결과를 쓴다. 쓸 결과가 없으면 빈 값이다.
     */
    Optional<RegionAddress> geocodedAddress(JsonNode response) {
        RegionAddress best = null;
        int bestRank = Integer.MAX_VALUE;
        for (JsonNode result : response.path("results")) {
            JsonNode components = result.path("addressComponents");
            if (!components.isArray()) {
                continue;
            }
            RegionAddress address = GoogleAddressMapper.address(components, null);
            int rank = geocodeRank(address);
            if (rank < bestRank) {
                best = address;
                bestRank = rank;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * 결과를 고르는 순서. 시·도가 있으면 국가만 있는 결과보다 앞선다(국가만으로는 한국 지역을 고를 수 없다).
     * 1 국가+시·도, 2 시·도(국가 없음, 독도처럼), 3 국가만, 4 국가 없이 지역 이름만. 지역 이름도 없으면 쓰지 않는다.
     * 같은 순위면 Google이 앞에 둔 결과를 쓴다.
     */
    private static int geocodeRank(RegionAddress address) {
        boolean country = address.countryCode() != null;
        boolean province = address.province() != null;
        if (country && province) {
            return 1;
        }
        if (province) {
            return 2;
        }
        if (country) {
            return 3;
        }
        return address.hasAreaNames() ? 4 : Integer.MAX_VALUE;
    }
}
