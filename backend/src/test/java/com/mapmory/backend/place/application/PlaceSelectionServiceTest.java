package com.mapmory.backend.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mapmory.backend.place.application.SelectedPlace;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.region.application.RegionSuggester;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionAddress;
import com.mapmory.backend.region.domain.RegionType;
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
        when(placeLookupPort.findDetails(PlaceId.from("ChIJ-gyeong"), "session-1"))
                .thenReturn(place(new RegionAddress("KR", "서울특별시", List.of("종로구"))));
        when(regionSuggester.suggest(new RegionAddress("KR", "서울특별시", List.of("종로구"))))
                .thenReturn(Optional.of(district));

        SelectedPlace result = service.select("ChIJ-gyeong", "session-1");

        assertThat(result.suggestedRegion()).isEqualTo(district);
        assertThat(result.manualRegionRequired()).isFalse();
    }

    @Test
    void 지역을_찾지_못하면_직접_선택하도록_알린다() {
        when(placeLookupPort.findDetails(PlaceId.from("ChIJ-island"), null)).thenReturn(place(RegionAddress.UNKNOWN));
        when(regionSuggester.suggest(RegionAddress.UNKNOWN)).thenReturn(Optional.empty());

        SelectedPlace result = service.select("ChIJ-island", null);

        assertThat(result.suggestedRegion()).isNull();
        assertThat(result.manualRegionRequired()).isTrue();
    }

    private static PlaceDetails place(RegionAddress address) {
        return new PlaceDetails(PlaceId.from("ChIJ-place"), null, address,
                new PlaceProvider("GOOGLE", "Google Maps", "https://www.google.com/maps"));
    }
}
