package com.mapmory.backend.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mapmory.backend.place.application.model.PlaceDetails;
import com.mapmory.backend.place.application.model.SelectedPlace;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionSuggester;
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
    @Mock RegionSuggester regionSuggester;
    @InjectMocks PlaceSelectionService service;

    @Test
    void 장소의_국가와_주소로_지역을_추천한다() {
        Region district = Region.of(null, null, "11110", "종로구", RegionType.DISTRICT);
        when(placeLookupPort.findForSelection("ChIJ-gyeong", "session-1"))
                .thenReturn(place("KR", List.of("서울특별시", "종로구")));
        when(regionSuggester.suggest("KR", List.of("서울특별시", "종로구"))).thenReturn(Optional.of(district));

        SelectedPlace result = service.select("ChIJ-gyeong", "session-1");

        assertThat(result.suggestedRegion()).isEqualTo(district);
    }

    @Test
    void 지역을_찾지_못하면_직접_선택하도록_알린다() {
        when(placeLookupPort.findForSelection("ChIJ-island", null)).thenReturn(place(null, List.of()));
        when(regionSuggester.suggest(null, List.of())).thenReturn(Optional.empty());

        SelectedPlace result = service.select("ChIJ-island", null);

        assertThat(result.suggestedRegion()).isNull();
    }

    private static PlaceDetails place(String countryCode, List<String> addressAreas) {
        return new PlaceDetails("ChIJ-place", null, countryCode, "Google Maps", "https://www.google.com/maps", addressAreas);
    }
}
