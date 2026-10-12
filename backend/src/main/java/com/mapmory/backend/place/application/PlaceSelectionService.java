package com.mapmory.backend.place.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.model.PlaceDetails;
import com.mapmory.backend.place.application.model.SelectedPlace;
import com.mapmory.backend.place.application.port.DistrictLocator;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.application.port.PlaceRateLimitPort;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionResolver;
import org.springframework.stereotype.Service;

@Service
public class PlaceSelectionService {

    private final PlaceLookupPort placeLookupPort;
    private final PlaceRateLimitPort rateLimitPort;
    private final DistrictLocator districtLocator;
    private final RegionResolver regionResolver;
    private final KoreanAddressRegionMatcher addressRegionMatcher;

    public PlaceSelectionService(
            PlaceLookupPort placeLookupPort,
            PlaceRateLimitPort rateLimitPort,
            DistrictLocator districtLocator,
            RegionResolver regionResolver,
            KoreanAddressRegionMatcher addressRegionMatcher
    ) {
        this.placeLookupPort = placeLookupPort;
        this.rateLimitPort = rateLimitPort;
        this.districtLocator = districtLocator;
        this.regionResolver = regionResolver;
        this.addressRegionMatcher = addressRegionMatcher;
    }

    public SelectedPlace select(Long memberId, String placeId, String sessionToken) {
        rateLimitPort.checkSelection(memberId);
        PlaceDetails place = placeLookupPort.findForSelection(placeId, sessionToken);
        return new SelectedPlace(place, suggestedRegion(place));
    }

    public Region suggestedRegion(PlaceDetails place) {
        if (place.countryCode() == null) {
            return null;
        }
        try {
            if (!"KR".equals(place.countryCode())) {
                return regionResolver.resolve(place.countryCode(), null, null);
            }
            if (place.regionByAddress()) {
                return addressRegionMatcher.match(place.addressAreas()).orElse(null);
            }
            if (place.latitude() == null || place.longitude() == null) {
                return null;
            }
            return districtLocator.find(place.longitude(), place.latitude())
                    .map(match -> regionResolver.resolve("KR", match.provinceCode(), match.districtCode()))
                    .orElse(null);
        } catch (BusinessException ignored) {
            // DB에 국가/시군구가 없거나 경계가 오래된 경우, 사용자가 지역을 직접 선택한다.
            return null;
        }
    }
}
