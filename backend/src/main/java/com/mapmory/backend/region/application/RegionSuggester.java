package com.mapmory.backend.region.application;

import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionAddress;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 장소의 주소로 기록에 붙일 지역을 추천한다. 대한민국은 시·군·구까지, 해외는 국가까지 찾는다.
 * 찾지 못하면 빈 값이고, 사용자가 지역을 직접 고른다.
 */
@Component
public class RegionSuggester {

    private final RegionResolver regionResolver;
    private final KoreanAddressRegionMatcher addressRegionMatcher;

    RegionSuggester(RegionResolver regionResolver, KoreanAddressRegionMatcher addressRegionMatcher) {
        this.regionResolver = regionResolver;
        this.addressRegionMatcher = addressRegionMatcher;
    }

    public Optional<Region> suggest(RegionAddress address) {
        String countryCode = address.countryCode();
        if (countryCode == null) {
            return Optional.empty();
        }

        if (!Region.KOREA.equals(countryCode)) {
            return regionResolver.findCountryIfPresent(countryCode);
        }

        return addressRegionMatcher.match(address);
    }
}
