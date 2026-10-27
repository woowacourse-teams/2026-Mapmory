package com.mapmory.backend.region;

import com.mapmory.backend.common.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 장소의 국가 코드와 주소 이름으로 기록에 붙일 지역을 추천한다. 대한민국은 시·군·구까지, 해외는 국가까지 찾는다.
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

    public Optional<Region> suggest(String countryCode, List<String> addressAreas) {
        if (countryCode == null) {
            return Optional.empty();
        }
        try {
            if (!Region.KOREA.equals(countryCode)) {
                return Optional.of(regionResolver.resolveCountry(countryCode));
            }
            return addressRegionMatcher.match(addressAreas);
        } catch (BusinessException ignored) {
            // DB에 국가가 없으면 사용자가 지역을 직접 선택한다.
            return Optional.empty();
        }
    }
}
