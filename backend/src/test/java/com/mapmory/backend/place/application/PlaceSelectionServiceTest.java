package com.mapmory.backend.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mapmory.backend.place.application.model.PlaceDetails;
import com.mapmory.backend.place.application.model.SelectedPlace;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionResolver;
import com.mapmory.backend.region.RegionType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceSelectionServiceTest {

    @Mock PlaceLookupPort placeLookupPort;
    @Mock RegionResolver regionResolver;
    @Mock KoreanAddressRegionMatcher addressRegionMatcher;
    @InjectMocks PlaceSelectionService service;

    @Test
    void 주소_이름으로_시군구를_추천한다() {
        Region country = Region.of(null, null, "KR", "대한민국", RegionType.COUNTRY);
        Region province = Region.of(country, country, "11", "서울특별시", RegionType.PROVINCE);
        Region district = Region.of(province, country, "11110", "종로구", RegionType.DISTRICT);
        when(placeLookupPort.findForSelection("ChIJ-gyeong", null))
                .thenReturn(place("KR", List.of("서울특별시", "종로구")));
        when(addressRegionMatcher.match(List.of("서울특별시", "종로구"))).thenReturn(Optional.of(district));

        SelectedPlace result = service.select("ChIJ-gyeong", null);

        assertThat(result.suggestedRegion()).isEqualTo(district);
    }

    @Test
    void 주소로_시군구를_찾지_못하면_직접_선택하도록_알린다() {
        when(placeLookupPort.findForSelection("ChIJ-gyeong", null)).thenReturn(place("KR", List.of()));
        when(addressRegionMatcher.match(List.of())).thenReturn(Optional.empty());

        SelectedPlace result = service.select("ChIJ-gyeong", null);

        assertThat(result.suggestedRegion()).isNull();
    }

    @Test
    void 해외_장소는_국가를_추천한다() {
        Region japan = Region.of(null, null, "JP", "일본", RegionType.COUNTRY);
        when(placeLookupPort.findForSelection("ChIJ-tokyo", null)).thenReturn(place("JP", List.of()));
        when(regionResolver.resolve("JP", null, null)).thenReturn(japan);

        SelectedPlace result = service.select("ChIJ-tokyo", null);

        assertThat(result.suggestedRegion()).isEqualTo(japan);
        verifyNoInteractions(addressRegionMatcher);
    }

    @Test
    void 제공자가_국가_코드를_주지_않으면_직접_선택하도록_알린다() {
        when(placeLookupPort.findForSelection("ChIJ-island", null)).thenReturn(place(null, List.of()));

        SelectedPlace result = service.select("ChIJ-island", null);

        assertThat(result.suggestedRegion()).isNull();
    }

    private static PlaceDetails place(String countryCode, List<String> addressAreas) {
        return new PlaceDetails("ChIJ-place", null, countryCode, "Google Maps", "https://www.google.com/maps", addressAreas);
    }
}
